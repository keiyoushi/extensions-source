package eu.kanade.tachiyomi.extension.pt.astratoons

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class Astratoons : HttpSource() {

    override val name = "Astratoons"

    override val baseUrl = "https://new.astratoons.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override val versionId: Int = 2

    // ======================== Popular ==========================

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#comicsSlider a").map(::mangaFromElement)
        return MangasPage(mangas, hasNextPage = false)
    }

    // ======================== Latest ==========================

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#latest-grid div[class*=card] > a").map(::mangaFromElement)
        return MangasPage(mangas, hasNextPage = false)
    }

    // ======================== Search ==========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/live-search".toHttpUrl().newBuilder()
            .setQueryParameter("q", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<SearchDto>>().map { it.toSManga(baseUrl) }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ======================== Details =========================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("img[class*=object-cover]")?.absUrl("src")
        description = document.selectFirst("div:has(>h1) + div")?.text()
        genre = document.select("div:has(h3) + div a:not([target])").joinToString { it.text() }
        author = document.selectFirst("span:contains(Autor) > span")?.text()
        artist = document.selectFirst("span:contains(Artista) > span")?.text()
    }

    // ======================== Chapter =========================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val document = client.newCall(mangaDetailsRequest(manga))
            .execute().asJsoup()

        val mangaId = MANGA_ID.find(document.selectFirst("main[x-data]")!!.attr("x-data"))
            ?.groupValues?.last()

        val urlBuilder = "$baseUrl/api/comics/$mangaId/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("search", "")
            .addQueryParameter("order", "desc")

        var page = 1
        val chapters = mutableListOf<SChapter>()
        do {
            val url = urlBuilder
                .setQueryParameter("page", (page++).toString())
                .build()

            val chapterListDto = client.newCall(GET(url, headers)).execute()
                .parseAs<ChapterListDto>()

            val fragment = chapterListDto.asJsoup()

            chapters += fragment.select("a").map(::chapterFromElement)
        } while (chapterListDto.hasMore)

        chapters
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    // ======================== Pages ===========================

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#reader-container img[src], #reader-container canvas[data-src]").mapIndexed { index, element ->
            val imageUrl = element.attr("src").ifEmpty { element.attr("data-src") }
            Page(index, document.location(), imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    // ========================== Utils =========================

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst(".text-lg")!!.text()
        setUrlWithoutDomain(element.absUrl("href"))
    }

    companion object {
        val MANGA_ID = """\d+""".toRegex()
    }
}
