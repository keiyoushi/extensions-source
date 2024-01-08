package eu.kanade.tachiyomi.extension.pt.lermanga

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.UnsupportedOperationException

class LerManga : ParsedHttpSource() {

    override val name = "Ler Mangá"

    override val baseUrl = "https://lermanga.org"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 1, TimeUnit.SECONDS)
        .rateLimitHost(IMG_CDN_URL.toHttpUrl(), 1, 2, TimeUnit.SECONDS)
        .build()

    private val json: Json by injectLazy()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/")

    private fun apiHeadersBuilder(): Headers.Builder = headersBuilder()
        .add("Accept", "application/json")

    private val apiHeaders by lazy { apiHeadersBuilder().build() }

    override fun popularMangaRequest(page: Int): Request {
        val path = if (page > 1) "page/$page/" else ""
        return GET("$baseUrl/mangas/$path?orderby=views&order=desc", headers)
    }

    override fun popularMangaSelector(): String = "div.film_list div.flw-item"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h3.film-name")!!.text()
        thumbnail_url = element.selectFirst("img.film-poster-img")!!.srcAttr()
        setUrlWithoutDomain(element.selectFirst("a.dynamic-name")!!.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String = "div.wp-pagenavi > a:last-child"

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "div.capitulo_recentehome"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")!!.absUrl("data-src")
        setUrlWithoutDomain(element.selectFirst("h3 > a")!!.attr("href"))
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SLUG_SEARCH)
            val tempManga = SManga.create().apply { url = "/mangas/$slug" }

            return mangaDetailsRequest(tempManga)
        }

        val apiRequest = "$API_BASE_URL/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("search", query)
            .addQueryParameter("_fields", "title,slug")
            .build()

        return GET(apiRequest, apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.queryParameter("slug") != null) {
            val manga = mangaDetailsParse(response)
            return MangasPage(listOf(manga), hasNextPage = false)
        }

        val result = response.parseAs<List<LmMangaDto>>()
        val mangaList = result.map(LmMangaDto::toSManga)

        val currentPage = response.request.url.queryParameter("page")
            .orEmpty().toIntOrNull() ?: 1
        val lastPage = response.headers["X-Wp-TotalPages"]!!.toInt()
        val hasNextPage = currentPage < lastPage

        return MangasPage(mangaList, hasNextPage)
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.removePrefix("/mangas/").removeSuffix("/")

        val apiRequest = "$API_BASE_URL/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("slug", slug)
            .addQueryParameter("limit", "1")
            .addQueryParameter("_embed", "wp:term")
            .addQueryParameter("_fields", "title,slug,content,_links.wp:term,_embedded.wp:term")
            .build()

        return GET(apiRequest, apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<List<LmMangaDto>>().first().toSManga()
    }

    override fun mangaDetailsParse(document: Document): SManga = throw UnsupportedOperationException("Not used")

    override fun chapterListSelector() = "div.manga-chapters div.single-chapter"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.selectFirst("a")!!.text()
        date_upload = element.selectFirst("small small")!!.text().toDate()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        val pagesScript = document.selectFirst("h1.heading-header + script")
            ?: return emptyList()

        val pagesJson = when {
            pagesScript.hasAttr("src") -> {
                pagesScript.attr("src")
                    .substringAfter("base64,")
                    .let { Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8) }
            }
            else -> pagesScript.data()
        }

        return pagesJson
            .replace(PAGES_VARIABLE_REGEX, "")
            .substringBeforeLast(";")
            .let { json.decodeFromString<List<String>>(it) }
            .mapIndexed { index, imageUrl ->
                Page(index, document.location(), imageUrl)
            }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(it.body.string())
    }

    private fun Element.srcAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(trim())?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        const val API_BASE_URL = "https://lermanga.org/wp-json/wp/v2"
        const val IMG_CDN_URL = "https://img.lermanga.org"
        private val PAGES_VARIABLE_REGEX = "var imagens_cap\\s*=\\s*".toRegex()
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("dd-MM-yyyy", Locale("pt", "BR"))
        }

        const val PREFIX_SLUG_SEARCH = "slug:"
        private const val ERROR_NO_SEARCH_AVAILABLE = "O site não possui busca própria."
    }
}
