package eu.kanade.tachiyomi.extension.all.comicfury

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
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ComicFury(
    override val lang: String,
    private val siteLang: String = lang, // override lang string used in MangaSearch
    private val extraName: String = "",
) : HttpSource(), ConfigurableSource {
    override val baseUrl: String = "https://comicfury.com"
    override val name: String = "Comic Fury$extraName" // Used for No Text
    override val supportsLatest: Boolean = true

    override val client = network.cloudflareClient.newBuilder().addInterceptor(TextInterceptor()).build()

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
    private val archiveSelector = "a:has(div.archive-chapter)"
    private val chapterSelector = "a:has(div.archive-comic)"
    private val nextArchivePageSelector = "#scroll-content > .onsite-viewer-back-link + .archive-pages a"
    private lateinit var currentPage: org.jsoup.nodes.Document

    private fun Element.toSManga(): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(this@toSManga.attr("abs:href"))
            name = this@toSManga.select(".archive-comic-title").text()
            date_upload = this@toSManga.select(".archive-comic-date").text().toDate()
        }
    }

    private fun collect(url: String): List<SChapter> {
        return client.newCall(GET(url, headers)).execute().asJsoup()
            .also { currentPage = it }
            .select(chapterSelector)
            .map { element -> element.toSManga() }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsp = response.asJsoup()

        return if (jsp.selectFirst(archiveSelector) != null) {
            val chapters = mutableListOf<SChapter>()
            jsp.select(archiveSelector).eachAttr("abs:href").map { url ->
                chapters.addAll(collect(url))
                currentPage.select(nextArchivePageSelector).eachAttr("abs:href")
                    .mapNotNull { nextUrl -> chapters.addAll(collect(nextUrl)) }
            }
            chapters
                .mapIndexed { index, sChapter -> sChapter.apply { chapter_number = index.toFloat() } }
                .reversed()
        } else {
            jsp.select(chapterSelector).mapIndexed { i, element ->
                element.toSManga().apply { chapter_number = "0.$i".toFloat() }
            }.reversed()
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
                                ?.let { "Author's Notes from $it" }
                                .orEmpty(),
                            jsp.selectFirst("div.is--comment-content")?.html().orEmpty(),
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

    private fun Boolean.toInt(): Int = if (this) { 0 } else { 1 }

    // START OF AUTHOR NOTES //
    private val preferences: SharedPreferences by getPreferencesLazy()
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
        throw UnsupportedOperationException()

    // Date stuff

    private fun String.toDate(): Long {
        // remove st nd rd th (e.g. from 4th) but not from AuguST, and commas
        val ret = this.replace(Regex("(?<=\\d)(st|nd|rd|th)|,"), "")

        return when {
            ret.contains(":") -> date[0].parseTime(ret)
            this.matches(Regex("\\d{1,2}\\s?\\w{3,9}\\s?\\w{2,4}")) -> date[1].parseTime(ret)
            this.matches(Regex("\\w{3,9}\\s?\\d{1,2}\\s?\\d{2,4}")) -> date[2].parseTime(ret)
            else -> 0
        }
    }

    private val date = listOf("dd MMM yyyy hh:mm aa", "dd MMM yyyy", "MMM dd yyyy")
        .map { SimpleDateFormat(it, Locale.US) }

    private fun SimpleDateFormat.parseTime(string: String): Long {
        return this.parse(string)?.time ?: 0
    }
}
