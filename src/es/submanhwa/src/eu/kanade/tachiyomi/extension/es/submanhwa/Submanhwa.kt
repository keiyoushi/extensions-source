package eu.kanade.tachiyomi.extension.es.submanhwa

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Submanhwa : ParsedHttpSource() {

    override val name = "Submanhwa"
    override val baseUrl = "https://www.submanhwa.com"
    override val lang = "es"
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("dd MMM. yyyy", Locale.ENGLISH)

    override fun popularMangaRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("filterList")
            addQueryParameter("page", page.toString())
            addQueryParameter("sortBy", "views")
            addQueryParameter("asc", "false")
        }.build(),
        headers,
    )

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("filterList")
            addQueryParameter("page", page.toString())
            addQueryParameter("sortBy", "views")
            addQueryParameter("asc", "false")
            addQueryParameter("alpha", query)
        }.build(),
        headers,
    )

    override fun popularMangaSelector(): String = ".series-card"

    override fun latestUpdatesSelector(): String = "div[class^=manga-item]"

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun popularMangaNextPageSelector(): String = "li a[rel=next]"

    override fun latestUpdatesNextPageSelector(): String = "no next page"

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".series-title")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        thumbnail_url = element.selectFirst("img")!!.absUrl("src")
    }

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h3[class^=manga-title] a")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        thumbnail_url = element.selectFirst("img")!!.absUrl("src")
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".widget-title")!!.text()
        thumbnail_url = document.selectFirst("img")?.absUrl("src")
        description = document.selectFirst("h5:contains(Resumen) + p")?.text()

        val box = document.selectFirst(".widget-container")

        status = when (box?.selectFirst("dt:contains(Estado) + dd span")?.text()?.lowercase()) {
            "completed" -> SManga.COMPLETED
            "cancelled" -> SManga.CANCELLED
            "ongoing" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }

        author = box?.selectFirst("dt:contains(Autor) + dd a")?.text()
        genre = box?.select("dt:contains(Categor) + dd a")?.joinToString { a -> a!!.text() }
    }

    override fun chapterListSelector(): String = ".chapters li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val a = element.selectFirst("a")
        name = a!!.text()
        setUrlWithoutDomain(a.absUrl("href"))

        val date = element.selectFirst(".date-chapter-title-rtl")!!.text()
        date_upload = dateFormat.tryParse(date)
    }

    override fun pageListParse(document: Document): List<Page> =
        document.select("#all img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("data-src"))
        }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()
}
