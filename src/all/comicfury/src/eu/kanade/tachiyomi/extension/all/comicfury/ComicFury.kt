package eu.kanade.tachiyomi.extension.all.comicfury

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class ComicFury(
    override val lang: String,
    private val siteLang: String = lang, // override lang string used in MangaSearch
    private val extraName: String = "",
) : HttpSource(), ConfigurableSource {
    override val baseUrl: String = "https://comicfury.com"
    override val name: String = "Comic Fury$extraName" //Used for No Text
    override val supportsLatest: Boolean = true
    private val dateFormat = SimpleDateFormat("dd MMM yyyy hh:mm aa", Locale.US)
    private val dateFormatSlim = SimpleDateFormat("dd MMM yyyy", Locale.US)

    override val client = super.client.newBuilder().addInterceptor(TextInterceptor()).build()

    /**
     * Archive is on a separate page from manga info
     */
    override fun chapterListRequest(manga: SManga): Request =
        GET("$baseUrl/read/${manga.url.substringAfter("?url=")}/archive")

    /**
     * Open Archive Url instead of the details page
     * Helps with getting past the nfsw pages
     */
    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/read/" + manga.url.substringAfter("?url=") + "/archive"
    }

    /**
     * There are two different ways chapters are setup
     * First Way if (true)
     *  Manga -> Chapter -> Comic -> Pages
     * The Second Way if (false)
     *  Manga -> Comic -> Pages
     *
     *  Importantly the Chapter And Comic Pages can be easy distinguished
     *  by the name of the list elements in this case archive-chapter/archive-comic
     *
     *  For Manga that doesn't have "chapters" skip the loop. Including All Sub-Comics of Chapters
     *
     *  Put the chapter name into scanlator so read can know what chapter it is.
     *
     *  Chapter Number is handled as Chapter dot Comic. Ex. Chapter 6, Comic 4: chapter_number = 6.4
     *
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        val jsp = response.asJsoup()
        if (jsp.selectFirst("div.archive-chapter") != null) {
            val chapters: MutableList<SChapter> = arrayListOf()
            for (chapter in jsp.select("div.archive-chapter").parents().reversed()) {
                val name = chapter.text()
                chapters.addAll(
                    client.newCall(
                        GET("$baseUrl${chapter.attr("href")}"),
                    ).execute()
                        .use { chapterListParse(it) }
                        .mapIndexed { i, it ->
                            it.apply {
                                scanlator = name
                                chapter_number += i
                            }
                        },
                )
            }
            return chapters
        } else {
            return jsp.select("div.archive-comic").mapIndexed { i, it ->
                SChapter.create().apply {
                    url = it.parent()!!.attr("href")
                    name = it.child(0).ownText()
                    date_upload = it.child(1).ownText().toDate()
                    chapter_number = "0.$i".toFloat()
                }
            }.toList().reversed()
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val jsp = response.asJsoup()
        val pages: MutableList<Page> = arrayListOf()
        val comic = jsp.selectFirst("div.is--comic-page")
        for (child in comic!!.select("div.is--image-segment div img")) {
            pages.add(
                Page(
                    pages.size,
                    response.request.url.toString(),
                    child.attr("src"),
                ),
            )
        }
        if (showAuthorsNotesPref()) {
            for (child in comic.select("div.is--author-notes div.is--comment-box").withIndex()) {
                pages.add(
                    Page(
                        pages.size,
                        response.request.url.toString(),
                        TextInterceptorHelper.createUrl(
                            jsp.selectFirst("a.is--comment-author")?.ownText()
                                ?: "Error No Author For Comment Found",
                            jsp.selectFirst("div.is--comment-content")?.html()
                                ?: "Error No Comment Content Found",
                        ),
                    ),
                )
            }
        }
        return pages
    }

    /**
     * Author name joining maybe redundant.
     *
     * Manga Status is available but not currently implemented.
     */
    override fun mangaDetailsParse(response: Response): SManga {
        val jsp = response.asJsoup()
        val desDiv = jsp.selectFirst("div.description-tags")
        return SManga.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
            description = desDiv?.parent()?.ownText()
            genre = desDiv?.children()?.eachText()?.joinToString(", ")
            author = jsp.select("a.authorname").eachText().joinToString(", ")
            initialized = true
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val jsp = response.asJsoup()
        val list: MutableList<SManga> = arrayListOf()
        for (result in jsp.select("div.webcomic-result")) {
            list.add(
                SManga.create().apply {
                    url = result.selectFirst("div.webcomic-result-avatar a")!!.attr("href")
                    title = result.selectFirst("div.webcomic-result-title")!!.attr("title")
                    thumbnail_url = result.selectFirst("div.webcomic-result-avatar a img")!!.absUrl("src")
                },
            )
        }
        return MangasPage(list, (jsp.selectFirst("div.search-next-page") != null))
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val req: HttpUrl.Builder = "$baseUrl/search.php".toHttpUrl().newBuilder()
        req.addQueryParameter("query", query)
        req.addQueryParameter("page", page.toString())
        req.addQueryParameter("language", siteLang)
        filters.forEach {
            when (it) {
                is TagsFilter -> req.addEncodedQueryParameter(
                    "tags",
                    it.state.replace(", ", ","),
                )
                is SortFilter -> req.addQueryParameter("sort", it.state.toString())
                is CompletedComicFilter -> req.addQueryParameter(
                    "completed",
                    it.state.toInt().toString(),
                )
                is LastUpdatedFilter -> req.addQueryParameter(
                    "lastupdate",
                    it.state.toString(),
                )
                is ViolenceFilter -> req.addQueryParameter("fv", it.state.toString())
                is NudityFilter -> req.addQueryParameter("fn", it.state.toString())
                is StrongLangFilter -> req.addQueryParameter("fl", it.state.toString())
                is SexualFilter -> req.addQueryParameter("fs", it.state.toString())
                else -> {}
            }
        }

        return Request.Builder().url(req.build()).build()
    }

    // START OF AUTHOR NOTES //
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }
    companion object {
        private const val SHOW_AUTHORS_NOTES_KEY = "showAuthorsNotes"
    }
    private fun showAuthorsNotesPref() =
        preferences.getBoolean(SHOW_AUTHORS_NOTES_KEY, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val authorsNotesPref = SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_AUTHORS_NOTES_KEY; title = "Show author's notes"
            summary = "Enable to see the author's notes at the end of chapters (if they're there)."
            setDefaultValue(false)
        }
        screen.addPreference(authorsNotesPref)
    }
    // END OF AUTHOR NOTES //

    // START OF FILTERS //
    override fun getFilterList(): FilterList = getFilterList(0)
    private fun getFilterList(sortIndex: Int): FilterList = FilterList(
        TagsFilter(),
        Filter.Separator(),
        SortFilter(sortIndex),
        Filter.Separator(),
        LastUpdatedFilter(),
        CompletedComicFilter(),
        Filter.Separator(),
        Filter.Header("Flags"),
        ViolenceFilter(),
        NudityFilter(),
        StrongLangFilter(),
        SexualFilter(),
    )

    internal class SortFilter(index: Int) : Filter.Select<String>(
        "Sort By",
        arrayOf("Relevance", "Popularity", "Last Update"),
        index,
    )
    internal class CompletedComicFilter : Filter.CheckBox("Comic Completed", false)
    internal class LastUpdatedFilter : Filter.Select<String>(
        "Last Updated",
        arrayOf("All Time", "This Week", "This Month", "This Year", "Completed Only"),
        0,
    )
    internal class ViolenceFilter : Filter.Select<String>(
        "Violence",
        arrayOf("None / Minimal", "Violent Content", "Gore / Graphic"),
        2,
    )
    internal class NudityFilter : Filter.Select<String>(
        "Frontal Nudity",
        arrayOf("None", "Occasional", "Frequent"),
        2,
    )
    internal class StrongLangFilter : Filter.Select<String>(
        "Strong Language",
        arrayOf("None", "Occasional", "Frequent"),
        2,
    )
    internal class SexualFilter : Filter.Select<String>(
        "Sexual Content",
        arrayOf("No Sexual Content", "Sexual Situations", "Strong Sexual Themes"),
        2,
    )
    internal class TagsFilter : Filter.Text("Tags")

    // END OF FILTERS //

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(page, "", getFilterList(1))
    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(page, "", getFilterList(2))
    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not Used")

    private fun String.toDate(): Long {
        val ret = this.replace("st", "")
            .replace("nd", "")
            .replace("rd", "")
            .replace("th", "")
            .replace(",", "")
        return dateFormat.parse(ret)?.time ?: dateFormatSlim.parse(ret)!!.time
    }

    private fun Boolean.toInt(): Int = if (this) { 0 } else { 1 }
}
