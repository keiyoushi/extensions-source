package eu.kanade.tachiyomi.extension.pt.kairostoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class KairosToons : HttpSource() {

    override val name = "Kairos Toons"
    override val baseUrl = "https://kairostoons.net"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    // ======================== Popular =========================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/todos/?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = mangaFromResponse(response)

    // ======================== Latest ==========================

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage = mangaFromResponse(response)

    // ======================== Search ==========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/live-search/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchDto>()
        return MangasPage(dto.results.map { it.toSManga(baseUrl) }, hasNextPage = false)
    }

    // ======================== Details =========================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst(".sidebar-cover-image img")?.absUrl("src")
        description = document.selectFirst(".manga-description")?.text()
        genre = document.select("a.genre-tag").joinToString { it.text() }
        status = when (document.selectFirst(".status-tag")?.text()?.lowercase()) {
            "em andamento" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    // ======================== Chapters ========================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        mutableListOf<SChapter>().apply {
            val urlBuilder = getMangaUrl(manga).toHttpUrl().newBuilder()
            var page = 1
            do {
                val url = urlBuilder
                    .setQueryParameter("page", (page++).toString())
                    .build()
                val document = client.newCall(GET(url, headers)).execute().asJsoup()
                addAll(chapterListParse(document))
            } while (document.selectFirst(hasNextPageSelector) != null)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    private fun chapterListParse(document: Document): List<SChapter> = document.select(".chapter-item-list a.chapter-link").map { element ->
        SChapter.create().apply {
            name = element.selectFirst(".chapter-number")!!.text()
            date_upload = DATE_FORMAT.tryParse(element.selectFirst(".chapter-date")?.ownText())
            setUrlWithoutDomain(element.absUrl("href"))
        }
    }

    // ======================== Pages ===========================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".chapter-image-canvas").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("data-src-url"))
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    // ======================== Utils ===========================

    private val hasNextPageSelector = ".page-link[aria-label=Próxima]:not(disabled)"

    private fun mangaFromResponse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document
            .select(".comics-grid a.comic-card-link, div.manga-card-simple")
            .map(::mangaFromElement)
        return MangasPage(mangas, document.selectFirst(hasNextPageSelector) != null)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        with(element) {
            setUrlWithoutDomain(absUrl("href").takeIf(String::isNotBlank) ?: selectFirst("a")!!.absUrl("href"))
        }
    }

    companion object {
        val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
    }
}
