package eu.kanade.tachiyomi.extension.en.apairof2

import eu.kanade.tachiyomi.lib.dataimage.DataImageInterceptor
import eu.kanade.tachiyomi.lib.dataimage.dataImageAsUrl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class APairOf2 : ParsedHttpSource() {

    override val name = "A Pair of 2+"

    override val baseUrl = "https://po2scans.com"

    override val lang = "en"

    override val supportsLatest = true

    override val versionId = 2

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(DataImageInterceptor())
        .rateLimit(4)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/series", headers)
    }

    override fun popularMangaSelector() = "div.series-list"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("div > h2").text()
            url = element.select("div > a").attr("href").let { "/$it" }
            thumbnail_url = element.select("img").attr("abs:data-src")
        }
    }

    // TODO: add page selectors & url parameters when site have enough series for pagination
    override fun popularMangaNextPageSelector() = null

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "div.chap"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.chap-title a").let { it ->
                url = it.attr("href").let { "/$it" }
                title = it.text()
            }
            thumbnail_url = element.select("img").attr("abs:data-src")
        }
    }

    override fun latestUpdatesNextPageSelector() = popularMangaSelector()

    // search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith(SLUG_SEARCH_PREFIX)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val url = "/series/${query.substringAfter(SLUG_SEARCH_PREFIX)}"
        return fetchMangaDetails(SManga.create().apply { this.url = url }).map {
            it.url = url
            MangasPage(listOf(it), false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/series?search=$query", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // manga details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.selectFirst(".title")!!.text()
            author = document.select(".author > span:nth-child(2)").text()
            artist = author
            status = document.select(".status > span:nth-child(2)").text().parseStatus()
            description = document.select(".summary p").text()
            thumbnail_url = document.select("div.series-image img").attr("abs:src")
        }
    }

    private fun String.parseStatus(): Int {
        return when {
            this.contains("ongoing", true) -> SManga.ONGOING
            this.contains("complete", true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // chapter list
    override fun chapterListSelector() = "div.chap"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let { a ->
                url = a.attr("href").let { "/$it" }
                name = a.text()
            }
            date_upload = parseDate(element.select("div > div > span:nth-child(2)").text())
        }
    }

    private fun parseDate(dateStr: String): Long {
        return runCatching { DATE_FORMATTER.parse(dateStr)?.time }
            .getOrNull() ?: 0L
    }

    // page list
    override fun pageListParse(document: Document): List<Page> {
        return document.select(".swiper-slide img").mapIndexed { index, img ->
            Page(
                index = index,
                imageUrl = img.imgAttr(),
            )
        }
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-pagespeed-high-res-src") -> dataImageAsUrl("data-pagespeed-high-res-src")
        hasAttr("data-pagespeed-lazy-src") -> dataImageAsUrl("data-pagespeed-lazy-src")
        else -> dataImageAsUrl("src")
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("not used")
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("dd MMMM, yy", Locale.ENGLISH)
        }

        const val SLUG_SEARCH_PREFIX = "slug:"
    }
}
