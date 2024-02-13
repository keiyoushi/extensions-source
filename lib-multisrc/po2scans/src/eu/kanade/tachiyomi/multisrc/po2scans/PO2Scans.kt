package eu.kanade.tachiyomi.multisrc.po2scans

import eu.kanade.tachiyomi.lib.dataimage.DataImageInterceptor
import eu.kanade.tachiyomi.lib.dataimage.dataImageAsUrl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

abstract class PO2Scans(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("dd MMMM, yy", Locale.ENGLISH),
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(DataImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/series", headers)

    override fun popularMangaSelector() = "div.series-list"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("div > a")!!.absUrl("href"))
        title = element.selectFirst("div > h2")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
    }

    // TODO: add page selectors & url parameters when site have enough series for pagination
    override fun popularMangaNextPageSelector() = null

    // latest
    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "div.chap"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("div.chap-title a")!!.let {
            setUrlWithoutDomain(it.absUrl("href"))
            title = it.text()
        }
        thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith(SLUG_SEARCH_PREFIX)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val url = "/series/${query.substringAfter(SLUG_SEARCH_PREFIX)}"
        return fetchMangaDetails(SManga.create().apply { this.url = url })
            .map {
                it.url = url
                MangasPage(listOf(it), false)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/series?search=$query", headers)

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

    // chapter list
    override fun chapterListSelector() = "div.chap"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("a")!!.let {
            setUrlWithoutDomain(it.absUrl("href"))
            name = it.text()
        }
        date_upload = parseDate(element.select("div > div > span:nth-child(2)").text())
    }

    // page list
    override fun pageListParse(document: Document) =
        document.select(".swiper-slide img").mapIndexed { index, img ->
            Page(index, imageUrl = img.imgAttr())
        }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private val statusOngoing = listOf("ongoing", "devam ediyor")
    private val statusCompleted = listOf("complete", "tamamlandı", "bitti")

    private fun String.parseStatus(): Int {
        return when (this.lowercase()) {
            in statusOngoing -> SManga.ONGOING
            in statusCompleted -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-pagespeed-high-res-src") -> dataImageAsUrl("data-pagespeed-high-res-src")
        hasAttr("data-pagespeed-lazy-src") -> dataImageAsUrl("data-pagespeed-lazy-src")
        else -> dataImageAsUrl("src")
    }

    private fun parseDate(dateStr: String) =
        runCatching { dateFormat.parse(dateStr)!!.time }
            .getOrDefault(0L)

    companion object {
        const val SLUG_SEARCH_PREFIX = "slug:"
    }
}
