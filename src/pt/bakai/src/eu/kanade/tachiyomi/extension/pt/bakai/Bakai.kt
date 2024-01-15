package eu.kanade.tachiyomi.extension.pt.bakai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Bakai : ParsedHttpSource() {

    override val name = "Bakai"

    override val baseUrl = "https://bakai.org"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client by lazy {
        network.client.newBuilder()
            .rateLimitHost(baseUrl.toHttpUrl(), 2)
            .build()
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/home1/page/$page/")

    override fun popularMangaSelector() = "#elCmsPageWrap ul > li > article"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        with(element.selectFirst("h2.ipsType_pageTitle a")!!) {
            title = text()
            setUrlWithoutDomain(attr("href"))
        }
    }

    override fun popularMangaNextPageSelector() = "li.ipsPagination_next > a[rel=next]"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException("Not used.")
    }

    override fun latestUpdatesSelector(): String {
        throw UnsupportedOperationException("Not used.")
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        throw UnsupportedOperationException("Not used.")
    }

    override fun latestUpdatesNextPageSelector(): String? {
        throw UnsupportedOperationException("Not used.")
    }

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
            val id = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/hentai/$id"))
                .asObservableSuccess()
                .map(::searchMangaByIdParse)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response.use { it.asJsoup() })
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        throw UnsupportedOperationException("Not used.")
    }

    override fun searchMangaSelector(): String {
        throw UnsupportedOperationException("Not used.")
    }

    override fun searchMangaFromElement(element: Element): SManga {
        throw UnsupportedOperationException("Not used.")
    }

    override fun searchMangaNextPageSelector(): String? {
        throw UnsupportedOperationException("Not used.")
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document): SManga {
        throw UnsupportedOperationException("Not used.")
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector(): String {
        throw UnsupportedOperationException("Not used.")
    }

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException("Not used.")
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        throw UnsupportedOperationException("Not used.")
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used.")
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
