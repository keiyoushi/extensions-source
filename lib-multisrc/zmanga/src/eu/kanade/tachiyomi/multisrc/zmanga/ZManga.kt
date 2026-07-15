package eu.kanade.tachiyomi.multisrc.zmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

abstract class ZManga : HttpSource() {

    protected open val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    override val supportsLatest = true

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/advanced-search/${pagePathSegment(page)}?order=popular")

    open fun popularMangaSelector() = "div.flexbox2-item"

    open fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.flexbox2-content a").attr("abs:href"))
        title = element.select("div.flexbox2-title > span").first()!!.text()
        thumbnail_url = element.select("img").attr("abs:src")
    }

    open fun popularMangaNextPageSelector() = "div.pagination .next"

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = document.select(popularMangaNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/advanced-search/${pagePathSegment(page)}?order=update")

    open fun latestUpdatesSelector() = popularMangaSelector()

    open fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    open fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }
        val hasNextPage = document.select(latestUpdatesNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/advanced-search/${pagePathSegment(page)}".toHttpUrl().newBuilder()
        url.addQueryParameter("title", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }
                is StatusFilter -> {
                    val status = when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "completed"
                        Filter.TriState.STATE_EXCLUDE -> "ongoing"
                        else -> ""
                    }
                    url.addQueryParameter("status", status)
                }
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is OrderByFilter -> {
                    url.addQueryParameter("order", filter.toUriPart())
                }
                is GenreList -> {
                    filter.state
                        .filter { it.state }
                        .forEach { url.addQueryParameter("genre[]", it.id) }
                }
                // if site has project page, default value "hasProjectPage" = false
                is ProjectFilter -> {
                    if (filter.toUriPart() == "project-filter-on") {
                        url = "$baseUrl$projectPageString/page/$page".toHttpUrl().newBuilder()
                    }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    open val projectPageString = "/project-list"

    open fun searchMangaSelector() = popularMangaSelector()

    open fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    open fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }
        val hasNextPage = document.select(searchMangaNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val thumb = document.select("div.series-thumb img")
        thumbnail_url = thumb.attr("data-lazy-src").takeIf { it.isNotBlank() }
            ?: thumb.attr("abs:src")
        author = document.select(".series-infolist li:contains(Author) span").text()
        artist = document.select(".series-infolist li:contains(Artist) span").text()
        status = parseStatus(document.select(".series-infoz .status").firstOrNull()?.ownText())
        description = document.select("div.series-synops").text()
        genre = document.select("div.series-genres a").joinToString { it.text() }

        // add series type(manga/manhwa/manhua/other) thinggy to genre
        document.select(seriesTypeSelector).firstOrNull()?.ownText()?.let {
            if (it.isNotEmpty() && it != "-" && genre?.contains(it, true) != true) {
                genre = if (genre.isNullOrEmpty()) it else "$genre, $it"
            }
        }

        // add alternative name to manga description
        document.select(altNameSelector).firstOrNull()?.ownText()?.let {
            if (it.isNotEmpty()) {
                description = if (description.isNullOrEmpty()) {
                    altName + it
                } else {
                    description + "\n\n$altName" + it
                }
            }
        }
    }

    open val seriesTypeSelector = "div.block span.type"
    open val altNameSelector = ".series-title span"
    open val altName = "Alternative Name: "

    // ============================= Chapters ==============================

    // careful not to include download links
    open fun chapterListSelector() = "ul.series-chapterlist div.flexch-infoz a"

    open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        name = element.select("span").first()!!.ownText()
        date_upload = dateFormat.tryParse(element.select("span.date").text())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { element ->
            chapterFromElement(element)
        }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())

    open fun pageListParse(document: Document): List<Page> = document.select("div.reader-area img:not(noscript img)").mapIndexed { i, img ->
        val imgUrl = img.attr("data-lazy-src").takeIf { it.isNotBlank() }
            ?: img.attr("abs:src")
        Page(i, imageUrl = imgUrl)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    open val hasProjectPage = false

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("You can combine filter."),
            Filter.Separator(),
            AuthorFilter(),
            YearFilter(),
            StatusFilter(),
            TypeFilter(),
            OrderByFilter(),
            GenreList(getGenreList()),
        )
        if (hasProjectPage) {
            filters.addAll(
                listOf(
                    Filter.Separator(),
                    Filter.Header("NOTE: cant be used with other filter!"),
                    Filter.Header("$name Project List page"),
                    ProjectFilter(),
                ),
            )
        }
        return FilterList(filters)
    }

    // ============================= Utilities =============================

    protected fun pagePathSegment(page: Int): String = if (page > 1) "page/$page/" else ""

    private fun parseStatus(status: String?): Int {
        val lowerCaseStatus = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            lowerCaseStatus.contains("ongoing") -> SManga.ONGOING
            lowerCaseStatus.contains("completed") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}
