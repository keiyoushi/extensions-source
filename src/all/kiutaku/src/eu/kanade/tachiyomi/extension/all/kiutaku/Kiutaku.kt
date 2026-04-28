package eu.kanade.tachiyomi.extension.all.kiutaku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class Kiutaku : HttpSource() {

    override val name = "Kiutaku"

    override val baseUrl = "https://kiutaku.com"

    override val lang = "all"

    override val id = 3040035304874076216

    override val supportsLatest = true

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimitHost(baseUrl.toHttpUrl(), 2)
            .build()
    }

    // ============================== Popular ===============================
    private fun getPage(page: Int) = (page - 1) * 20

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/hot?start=${getPage(page)}", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.blog > div.items-row").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("nav > a.pagination-next:not([disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a.item-link")!!.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        title = element.selectFirst("h2")?.text() ?: "Cosplay"
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?start=${getPage(page)}", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith(PREFIX_SEARCH)) {
        val id = query.removePrefix(PREFIX_SEARCH)
        client.newCall(GET("$baseUrl/$id"))
            .asObservableSuccess()
            .map(::searchMangaByIdParse)
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response)
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("start", getPage(page).toString())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        title = document.selectFirst("div.article-header")?.text() ?: "Cosplay"
        genre = document.selectFirst("div.article-tags")
            ?.select("a.tag > span")
            ?.eachText()
            ?.joinToString { it.trimStart('#') }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("nav.pagination:first-of-type a")
        .map(::chapterFromElement)
        .reversed()

    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        val text = element.text()
        name = "Page $text"
        chapter_number = text.toFloatOrNull() ?: 1F
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select("div.article-fulltext img[src]")
        .mapIndexed { index, item ->
            Page(index, imageUrl = item.absUrl("src"))
        }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
