package eu.kanade.tachiyomi.extension.es.leermangasxyz

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URLEncoder

open class LeerMangasXYZ : ParsedHttpSource() {

    override val baseUrl: String = "https://r1.leermanga.xyz"

    override val lang: String = "es"

    override val name: String = "LeerManga.xyz"

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override val supportsLatest: Boolean = false

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val row = element.select("td")
        with(row[0]) {
            chapter_number = text().toFloat()
            date_upload = 0
        }
        with(row[1]) {
            name = text()
            url = selectFirst("a")!!.attr("href")
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = super.fetchChapterList(manga).map {
        it.reversed()
    }
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        setUrlWithoutDomain(document.baseUri())
        val rawStatus = document.selectFirst("td:contains(Status)")!!.text()
        status = getStatus(rawStatus.substringAfter("Status: "))
        author = document.select("li[itemprop=author]").joinToString(separator = ", ") { it.text() }
        thumbnail_url = document.selectFirst("img.img-thumbnail")!!.attr("abs:src")
        description = document.selectFirst("p[itemprop=description]")!!.text()
        genre = document.select("span[itemprop=genre]").joinToString(", ") { it.text() }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = document.select(pageListSelector()).map {
            Page(
                imageUrl = it.attr("href"),
                index = it.attr("data-ngdesc").substringAfter("Page ").toInt(),
            )
        }
        if (pages.isEmpty()) {
            throw RuntimeException("Cannot fetch images from source")
        }
        return pages
    }

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img.card-img-top")!!.attr("abs:src")
        element.selectFirst("div.card-body")!!.let {
            val dc = it.selectFirst("h5.card-title a")!!
            url = dc.attr("href")
            title = dc.text()
        }
    }

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        with(element) {
            thumbnail_url = selectFirst("img")!!.attr("abs:src")
            title = selectFirst("span[itemprop=name]")!!.text()
            url = selectFirst("div.col-4 a")!!.attr("href")
        }
    }

    private fun encodeString(str: String): String = URLEncoder.encode(str, "utf-8")

    private fun getStatus(str: String): Int = when (str) {
        "Emitiéndose", "Ongoing", "En emisión" -> SManga.ONGOING
        "Finalizado" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
    // ========------- [[<  Request >]]] =========--------

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/search?query=${encodeString(query)}&page=$page")

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    // ------ ======== [[[ SELECTORS ]]] ======== -------

    private fun pageListSelector() = "div[data-nanogallery2] a"

    override fun searchMangaSelector(): String = "div[itemtype*=ComicSeries]"

    override fun searchMangaNextPageSelector(): String = "CHANGE THIS"

    override fun popularMangaSelector(): String = "div.card-group div.card"

    override fun popularMangaNextPageSelector(): String = "CHANGE THIS"

    override fun chapterListSelector(): String = "table#chaptersTable tbody tr"
}
