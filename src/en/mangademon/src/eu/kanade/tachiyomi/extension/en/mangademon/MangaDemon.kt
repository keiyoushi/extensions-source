package eu.kanade.tachiyomi.extension.en.mangademon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URLEncoder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class MangaDemon : ParsedHttpSource() {

    override val versionId = 2

    override val lang = "en"
    override val supportsLatest = true
    override val name = "Manga Demon"
    override val baseUrl = "https://ciorti.online"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/advanced.php?list=$page&status=all&orderby=VIEWS%20DESC", headers)
    }

    override fun popularMangaNextPageSelector() = "div.pagination > ul > a > li:contains(Next)"

    override fun popularMangaSelector() = "div#advanced-content > div.advanced-element"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.encodedAttr("href"))
        title = element.selectFirst("h1")!!.ownText()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/lastupdates.php?list=$page", headers)
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesSelector() = "div#updates-container > div.updates-element"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        with(element.selectFirst("div.updates-element-info")!!) {
            setUrlWithoutDomain(selectFirst("a")!!.encodedAttr("href"))
            title = selectFirst("a")!!.ownText()
        }
        thumbnail_url = element.selectFirst("div.thumb img")!!.attr("abs:src")
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.isNotEmpty()) {
            super.fetchSearchManga(page, query, filters)
        } else {
            client.newCall(filterSearchRequest(page, filters))
                .asObservableSuccess()
                .map(::filterSearchParse)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("manga", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = "body > a[href]"

    override fun searchMangaNextPageSelector() = null

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.encodedAttr("href"))
        title = element.selectFirst("div.seach-right > div")!!.ownText()
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
    }

    private fun filterSearchRequest(page: Int, filters: FilterList): Request {
        val url = "$baseUrl/advanced.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("list", page.toString())
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        filter.checked.forEach { genre ->
                            addQueryParameter("genre[]", genre)
                        }
                    }
                    is StatusFilter -> {
                        addQueryParameter("status", filter.selected)
                    }
                    is SortFilter -> {
                        addQueryParameter("orderby", filter.selected)
                    }
                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    private fun filterSearchParse(response: Response) = popularMangaParse(response)

    override fun getFilterList() = getFilters()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        with(document.selectFirst("div#manga-info-container")!!) {
            title = selectFirst("h1.big-fat-titles")!!.ownText()
            thumbnail_url = selectFirst("div#manga-page img")!!.attr("abs:src")
            genre = select("div.genres-list > li").joinToString { it.text() }
            description = selectFirst("div#manga-info-rightColumn > div > div.white-font")!!.text()
            author = select("div#manga-info-stats > div:has(> li:eq(0):contains(Author)) > li:eq(1)").text()
            status = parseStatus(select("div#manga-info-stats > div:has(> li:eq(0):contains(Status)) > li:eq(1)").text())
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        status.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div#chapters-list a.chplinks"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.encodedAttr("href"))
        name = element.ownText()
        date_upload = parseDate(element.selectFirst("span")?.ownText())
    }

    private fun parseDate(dateStr: String?): Long {
        return try {
            dateStr?.let { DATE_FORMATTER.parse(it)?.time } ?: 0
        } catch (_: ParseException) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.imgholder").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    private fun Element.encodedAttr(attribute: String) = URLEncoder.encode(attr(attribute), "UTF-8")

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    companion object {
        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    }
}
