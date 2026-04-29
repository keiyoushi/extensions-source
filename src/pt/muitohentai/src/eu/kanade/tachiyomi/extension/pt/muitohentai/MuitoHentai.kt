package eu.kanade.tachiyomi.extension.pt.muitohentai

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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.util.Locale
import java.util.concurrent.TimeUnit

class MuitoHentai : HttpSource() {

    override val name = "Muito Hentai"

    override val baseUrl = "https://www.muitohentai.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/")

    // The source does not have a popular list page, so we use the list instead.
    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", if (page == 1) baseUrl else "$baseUrl/mangas/${page - 1}")
            .build()

        val pageStr = if (page != 1) page.toString() else ""
        return GET("$baseUrl/mangas/$pageStr", newHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#archive-content article.tvshows").map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst("#paginacao a:last-child:contains(»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.data h3 a")!!.text()
        thumbnail_url = element.selectFirst("div.poster img")!!.attr("abs:src")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.lancamento-cap2 > li").map { latestUpdatesFromElement(it) }
        val hasNextPage = document.selectFirst("#paginacao a:last-child:contains(»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h2")!!.text()
        thumbnail_url = element.selectFirst("div.capaMangaHentai img")!!.attr("abs:src")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = "$baseUrl/buscar-manga/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .toString()

        return GET(searchUrl, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#archive-content article.tvshows").map { popularMangaFromElement(it) }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            author = document.selectFirst("div:has(strong:contains(Autor))")?.ownText()
            genre = document.select("a.genero_btn").joinToString {
                it.text().replaceFirstChar { ch -> ch.titlecase(LOCALE) }
            }
            description = document.selectFirst("div.backgroundpost:contains(Sinopse)")?.ownText()
            thumbnail_url = document.selectFirst("#capaAnime img")?.attr("abs:src")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.backgroundpost:contains(Capítulos de) h3 > a")
            .map { chapterFromElement(it) }
            .reversed()
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.ownText()
        setUrlWithoutDomain(element.attr("abs:href"))
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeader = headersBuilder()
            .set("Referer", "$baseUrl${chapter.url}".substringBeforeLast("/"))
            .build()

        return GET(baseUrl + chapter.url, newHeader)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.selectFirst("script:containsData(numeroImgAtual)")
            ?.data()
            ?.substringAfter("var arr = ")
            ?.substringBefore(";")
            ?.parseAs<JsonArray>()
            ?.mapIndexed { i, el ->
                Page(i, url = document.location(), imageUrl = el.jsonPrimitive.content)
            }
            .orEmpty()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    companion object {
        private val LOCALE = Locale("pt", "BR")
    }
}
