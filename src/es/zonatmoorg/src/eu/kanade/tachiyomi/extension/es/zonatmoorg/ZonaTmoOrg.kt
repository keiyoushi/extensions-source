package eu.kanade.tachiyomi.extension.es.zonatmoorg

import android.app.Application
import android.webkit.WebSettings
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class ZonaTmoOrg : HttpSource() {

    override val name = "ZonaTMO"
    override val baseUrl = "https://zonatmo.org"
    override val lang = "es"
    override val supportsLatest = true

    // ──────────────────── Cloudflare / UA ─────────────────────

    private val webViewUserAgent: String? by lazy {
        runCatching { WebSettings.getDefaultUserAgent(Injekt.get<Application>()) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::cloudflareInterceptor)
        .rateLimit(2, 1.seconds)
        .build()

    private fun cloudflareInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 403 || response.header("cf-ray") != null || response.code == 503) {
            val document = response.peekBody(Long.MAX_VALUE).string()
            if (document.contains("Un momento") || document.contains("Cloudflare") || document.contains("Just a moment")) {
                response.close()
                CloudflareResolver.resolve(
                    loadUrl = baseUrl,
                    cookieUrl = baseUrl,
                    userAgent = webViewUserAgent,
                )
                return chain.proceed(request)
            }
        }
        return response
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().apply {
        add("Referer", "$baseUrl/")
        webViewUserAgent?.let { set("User-Agent", it) }
    }

    // ──────────────────── Popular ─────────────────────

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/biblioteca?order_item=likes_count&order_dir=desc&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val elements = document.select("div.element, div.book-item, .element-bg")

        val mangas = elements.map { element ->
            SManga.create().apply {
                val a = element.selectFirst("a[href*=/library/]") ?: element.selectFirst("a")!!
                setUrlWithoutDomain(a.attr("href"))
                title = a.selectFirst("h4, .title")?.text() ?: a.attr("title")
                thumbnail_url = element.selectFirst("style")?.data()?.substringAfter("url('")?.substringBefore("')")
                    ?: element.selectFirst("img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("ul.pagination li a[rel=next], a:contains(Siguiente), a:contains(»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ──────────────────── Latest ─────────────────────

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/biblioteca?order_item=creation&order_dir=desc&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ──────────────────── Search ─────────────────────

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/biblioteca".toHttpUrl().newBuilder()
        url.addQueryParameter("title", query)
        url.addQueryParameter("page", page.toString())
        return GET(url.build().toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ──────────────────── Details ─────────────────────

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1, h2.title")?.text() ?: ""
            description = document.selectFirst(".description, .sinopsis")?.text()
            thumbnail_url = document.selectFirst(".book-thumbnail img, .thumb img")?.absUrl("src")
            author = document.select(".author a").joinToString { it.text() }
            genre = document.select(".genres a, .badge-primary").joinToString { it.text() }
            status = parseStatus(document.selectFirst(".status, .book-status")?.text())
        }
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "publicándose", "en emisión", "ongoing" -> SManga.ONGOING
        "finalizado", "completado", "completed" -> SManga.COMPLETED
        "pausado", "hiatus" -> SManga.ON_HIATUS
        "cancelado", "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ──────────────────── Chapters ─────────────────────

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val elements = document.select("ul.chapters-list li, .upload-link, .chapter-list li")

        return elements.map { element ->
            SChapter.create().apply {
                val a = element.selectFirst("a")!!
                setUrlWithoutDomain(a.attr("href"))
                name = a.text().trim()
                val numStr = name.replace(Regex("[^0-9.]"), "")
                chapter_number = numStr.toFloatOrNull() ?: -1f
            }
        }
    }

    // ──────────────────── Pages ─────────────────────

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()

        // 1. Check for img tags directly in viewer
        val imgElements = document.select("#viewer img, .viewer img, #app img")
        if (imgElements.isNotEmpty()) {
            imgElements.forEachIndexed { i, img ->
                val url = img.absUrl("data-src").ifEmpty { img.absUrl("src") }
                if (url.isNotEmpty()) pages.add(Page(i, "", url))
            }
            return pages
        }

        // 2. Check for JS array (common in TMO)
        val script = document.select("script").find { it.data().contains("var images =") || it.data().contains("images:") }
        if (script != null) {
            val data = script.data()
            val regex = Regex("""(https?://[^"']+\.(png|jpe?g|webp|gif))""")
            regex.findAll(data).forEachIndexed { i, matchResult ->
                pages.add(Page(i, "", matchResult.value))
            }
        }

        return pages.distinctBy { it.imageUrl }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    companion object {
        const val PREFIX_SLUG_SEARCH = "slug:"
    }
}
