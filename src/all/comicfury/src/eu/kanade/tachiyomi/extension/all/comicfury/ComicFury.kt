package eu.kanade.tachiyomi.extension.all.comicfury

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ComicFury(
    override val lang: String,
    private val siteLang: String = lang, // override lang string used in MangaSearch
    private val extraName: String = "",
) : HttpSource(),
    ConfigurableSource {
    override val baseUrl: String = "https://comicfury.com"
    override val name: String = "Comic Fury$extraName" // Used for No Text
    override val supportsLatest: Boolean = true

    override val client = network.client.newBuilder()
        .addInterceptor(ContentWarningInterceptor())
        .addInterceptor(TextInterceptor())
        .build()

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", getFilterList(1))
    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ========================= Latest =========================
    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", getFilterList(2))
    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ========================= Search =========================
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

        return GET(req.build(), headers)
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

    // ========================= Filters =========================
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

    private fun Boolean.toInt(): Int = if (this) {
        0
    } else {
        1
    }

    // ========================= Details =========================
    override fun mangaDetailsParse(response: Response): SManga {
        val jsp = response.asJsoup()
        val desDiv = jsp.selectFirst("div.description-tags")
        return SManga.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
            // If the description-tags div is null (common on profile pages or custom layout pages),
            // fallback to the custom layouts selector (username-and-title em).
            description = desDiv?.parent()?.ownText()
                ?: jsp.selectFirst("div.username-and-title em")?.text()
            // Fallback to parsing from the profile page's authorinfo block when description-tags is null.
            genre = desDiv?.children()?.eachText()?.joinToString(", ")
                ?: jsp.select("div.authorinfo:contains(Genre) a").eachText().joinToString(", ")
            author = jsp.select("a.authorname").eachText().joinToString(", ")
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/read/" + manga.url.substringAfter("?url=") + "/archive"

    // ========================= Chapters =========================
    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/read/${manga.url.substringAfter("?url=")}/archive")

    private val archiveSelector = "a:has(div.archive-chapter)"
    private val chapterSelector = "a:has(div.archive-comic)"
    private val nextPageSelector = "span.vfpagecurrent + a.vfpage"

    private fun Element.toSManga(chapterHeader: String? = null): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(this@toSManga.absUrl("href"))
        val comicName = this@toSManga.select(".archive-comic-title").text()
        name = if (chapterHeader.isNullOrEmpty()) comicName else "$chapterHeader - $comicName"
        date_upload = this@toSManga.select(".archive-comic-date").text().toDate()
    }

    private fun collect(startPage: Document, chapterHeader: String? = null): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var currentPage = startPage

        while (true) {
            // Get all chapters on the current page.
            chapters.addAll(
                currentPage.select(chapterSelector).map { element ->
                    element.toSManga(chapterHeader)
                },
            )

            // Fetch the next page and repeat. If there are no more pages, exit.
            val nextPageButton = currentPage.selectFirst(nextPageSelector) ?: break
            val url = nextPageButton.absUrl("href")
            currentPage = client.newCall(GET(url, headers)).execute().asJsoup()
        }

        return chapters
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsp = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        val archiveElements = jsp.select(archiveSelector)
        if (archiveElements.isNotEmpty()) {
            archiveElements.forEach { element ->
                val url = element.absUrl("href")
                val chapterHeader = element.select(".archive-chapter-title").text().ifEmpty { element.text() }
                val currentPage = client.newCall(GET(url, headers)).execute().asJsoup()
                chapters.addAll(collect(currentPage, chapterHeader))
            }
        } else {
            chapters.addAll(collect(jsp))
        }

        // Fallback when "Infinite Scroll View" is disabled by the author.
        // We fetch and parse the custom layout site under <slug>.webcomic.ws.
        if (chapters.isEmpty()) {
            val pathSegments = response.request.url.pathSegments
            val readIndex = pathSegments.indexOf("read")
            val slug = if (readIndex != -1 && readIndex + 1 < pathSegments.size) pathSegments[readIndex + 1] else ""
            if (slug.isNotEmpty()) {
                val customUrl = "https://$slug.webcomic.ws/archive/comics"
                try {
                    val customDoc = client.newCall(GET(customUrl, headers)).execute().asJsoup()
                    customDoc.select("div.archivecomic, div.nl-archivecomic").forEach { element ->
                        val linkElement = element.selectFirst("a") ?: return@forEach
                        val chapterHeader = element.parent()?.previousElementSibling()?.selectFirst("h3")?.text()
                        chapters.add(
                            SChapter.create().apply {
                                url = linkElement.absUrl("href")
                                val comicName = linkElement.text()
                                name = if (chapterHeader.isNullOrEmpty()) comicName else "$chapterHeader - $comicName"
                                date_upload = element.selectFirst(".comicposttime, .nl-archivecomicposttime")?.text()?.toDate() ?: 0L
                            },
                        )
                    }
                } catch (_: Exception) {
                    // Ignore and return empty list
                }
            }
        }

        return chapters
            .mapIndexed { index, sChapter -> sChapter.apply { chapter_number = index.toFloat() } }
            .reversed()
    }

    // ========================= Pages =========================
    override fun pageListRequest(chapter: SChapter): Request {
        val url = if (chapter.url.startsWith("http")) {
            chapter.url
        } else {
            "$baseUrl${chapter.url}"
        }
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val jsp = response.asJsoup()
        val pages: MutableList<Page> = arrayListOf()
        val comic = jsp.selectFirst("div.is--comic-page")
        if (comic != null) {
            // Infinite Scroll layout (default)
            for (child in comic.select("div.is--image-segment div img")) {
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
        } else {
            // Custom layout fallback (Infinite Scroll disabled)
            val images = jsp.select("#comicimage")
            for (child in images) {
                pages.add(
                    Page(
                        pages.size,
                        response.request.url.toString(),
                        child.attr("src"),
                    ),
                )
            }
        }
        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // START OF AUTHOR NOTES //
    private val preferences: SharedPreferences by getPreferencesLazy()

    private fun showAuthorsNotesPref() = preferences.getBoolean(SHOW_AUTHORS_NOTES_KEY, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val authorsNotesPref = SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_AUTHORS_NOTES_KEY
            title = "Show author's notes"
            summary = "Enable to see the author's notes at the end of chapters (if they're there)."
            setDefaultValue(false)
        }
        screen.addPreference(authorsNotesPref)
    }
    // END OF AUTHOR NOTES //

    // Date stuff

    private fun String.toDate(): Long {
        // remove st nd rd th (e.g. from 4th) but not from AuguST, and commas
        val ret = ordinalRegex.replace(this, "").trim()

        return when {
            ret.contains(":") -> date[0].parseTime(ret)
            dateRegex1.matches(ret) -> date[1].parseTime(ret)
            dateRegex2.matches(ret) -> date[2].parseTime(ret)
            dotDateRegex.matches(ret) -> date[3].parseTime(ret)
            else -> 0
        }
    }

    private val date = listOf("dd MMM yyyy hh:mm aa", "dd MMM yyyy", "MMM dd yyyy", "d.M.yyyy")
        .map { SimpleDateFormat(it, Locale.US) }

    private fun SimpleDateFormat.parseTime(string: String): Long = this.parse(string)?.time ?: 0

    companion object {
        private const val SHOW_AUTHORS_NOTES_KEY = "showAuthorsNotes"
        private val ordinalRegex = Regex("(?<=\\d)(st|nd|rd|th)|,")
        private val dateRegex1 = Regex("\\d{1,2}\\s?\\w{3,9}\\s?\\w{2,4}")
        private val dateRegex2 = Regex("\\w{3,9}\\s?\\d{1,2}\\s?\\d{2,4}")
        private val dotDateRegex = Regex("\\d{1,2}\\.\\d{1,2}\\.\\d{4}")
    }
}
