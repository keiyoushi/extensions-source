package eu.kanade.tachiyomi.extension.pt.bakai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.concurrent.TimeUnit

class Bakai : ParsedHttpSource() {

    override val name = "Bakai"

    override val baseUrl = "https://bakai.org"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimitHost(baseUrl.toHttpUrl(), 1, 2, TimeUnit.SECONDS)
            .cookieJar(
                object : CookieJar {
                    private fun List<Cookie>.removeLimit() = filterNot {
                        it.name.startsWith("ips4_") || it.path == "/search1"
                    }

                    private val cookieJar = network.client.cookieJar

                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) =
                        cookieJar.saveFromResponse(url, cookies.removeLimit())

                    override fun loadForRequest(url: HttpUrl) =
                        cookieJar.loadForRequest(url).removeLimit()
                },
            )
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", baseUrl)
        .set("Cache-Control", "no-cache")
        .set("Sec-Fetch-Dest", "image")
        .set("Sec-Fetch-Mode", "no-cors")
        .set("Sec-Fetch-Site", "same-site")
        .set("Sec-GPC", "1")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/home3/page/$page/")

    override fun popularMangaSelector() = "#elCmsPageWrap ul > li > article"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.imgAttr()
        with(element.selectFirst("h2.ipsType_pageTitle a")!!) {
            title = text()
            setUrlWithoutDomain(attr("href"))
        }
    }

    override fun popularMangaNextPageSelector() = "li.ipsPagination_next:not(.ipsPagination_inactive) > a[rel=next]"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesSelector(): String {
        throw UnsupportedOperationException("Not used.")
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
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
        val url = "$baseUrl/search3/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", "cms_records1")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sortby", "relevancy")
            .addQueryParameter("search_and_or", "or")
            .build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "ol > li > div"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.selectFirst(".ipsThumb img")?.imgAttr()

        with(element.selectFirst("h2.ipsStreamItem_title a")!!) {
            title = text()
            setUrlWithoutDomain(attr("href"))
        }
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.ipsType_pageTitle")?.text() ?: "Hentai"
        thumbnail_url = document.selectFirst("div.cCmsRecord_image img")?.imgAttr()
        artist = document.selectFirst("span.mangaInfo:has(strong:contains(Artist)) + a")?.text()
        genre = document.selectFirst("span.mangaInfo:has(strong:contains(Tags)) + span")?.text()
        description = document.selectFirst("h2.ipsFieldRow_desc")?.let {
            // Alternative titles
            "Títulos alternativos: ${it.text()}"
        }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    // ============================== Chapters ==============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            name = "Hentai"
            chapter_number = 1F
            url = manga.url
        }

        return Observable.just(listOf(chapter))
    }

    override fun chapterListSelector(): String {
        throw UnsupportedOperationException("Not used.")
    }

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException()
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.ipsGrid div.ipsType_center > img")
            .mapIndexed { index, item ->
                Page(index, "", item.imgAttr())
            }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    private fun Element.imgAttr(): String {
        return when {
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
            else -> attr("abs:src")
        }
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
