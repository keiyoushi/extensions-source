package eu.kanade.tachiyomi.extension.en.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import me.marplex.cloudflarebypass.CloudflareHTTPClient
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import kotlinx.coroutines.runBlocking

class NiaddEn : ParsedHttpSource() {

    override val name: String = "Niadd"
    override val baseUrl: String = "https://www.niadd.com"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.client.newBuilder().build()

    private val customHeaders: Headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0")
        .build()

    // Cloudflare client
    private val cfClient by lazy { CloudflareHTTPClient() }

    // ----------------------- POPULAR / LATEST / SEARCH -----------------------
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/category/?page=$page", headers = customHeaders)

    override fun popularMangaSelector(): String = "div.manga-item:has(a[href*='/manga/'])"
    override fun popularMangaFromElement(element: Element): SManga { /* ...mesmo código... */ }
    override fun popularMangaNextPageSelector(): String? = "a.next"

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/list/New-Update/?page=$page", headers = customHeaders)
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val q = URLEncoder.encode(query, "UTF-8")
        return GET("$baseUrl/search/?name=$q&page=$page", headers = customHeaders)
    }
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    // ----------------------- DETAILS -----------------------
    override fun mangaDetailsParse(document: Document): SManga { /* ...mesmo código... */ }
    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used.")

    // ----------------------- CHAPTERS -----------------------
    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/").substringBefore(".html")
        val nineUrl = "https://www.nineanime.com/manga/$slug.html?waring=1"
        // Usando Cloudflare client
        return runBlocking {
            val resp = cfClient.get(nineUrl) { headers(customHeaders) }
            GET(nineUrl, headers = customHeaders) // apenas retorna o request padrão pro ParsedHttpSource
        }
    }

    override fun chapterListSelector(): String = "ul.detail-chlist li a"
    override fun chapterFromElement(element: Element): SChapter { /* ...mesmo código... */ }

    // ----------------------- PAGES -----------------------
    override fun pageListRequest(chapter: SChapter): Request = GET(chapter.url, headers = customHeaders)

    override fun pageListParse(document: Document): List<Page> {
        val html = document.html()

        val arrayRegex = Regex("all_imgs_url:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL)
        val urlRegex = Regex("\"(https?://[^\"]+)\"")

        arrayRegex.find(html)?.let { match ->
            val urlsBlock = match.groupValues[1]
            val urls = urlRegex.findAll(urlsBlock).map { it.groupValues[1] }.toList()
            if (urls.isNotEmpty()) {
                return urls.mapIndexed { i, url -> Page(i, "", url) }
            }
        }

        val directImgs = document.select("div.reader-area img")
        if (directImgs.isNotEmpty()) {
            return directImgs.mapIndexed { i, img ->
                val url = img.absUrl("data-src").ifBlank { img.absUrl("src") }
                Page(i, "", url)
            }
        }

        val redirect = document.selectFirst("meta[http-equiv=refresh]")
            ?.attr("content")?.substringAfter("url=")?.trim()

        if (!redirect.isNullOrEmpty()) {
            return runBlocking {
                val resp = cfClient.get(redirect.toHttpUrlOrNull()!!) { headers(customHeaders) }
                val doc = Jsoup.parse(resp.body?.string().orEmpty(), redirect)
                val imgs = doc.select("div.reader-area img")
                imgs.mapIndexed { i, img ->
                    val url = img.absUrl("data-src").ifBlank { img.absUrl("src") }
                    Page(i, "", url)
                }
            }
        }

        throw Exception("No images or redirect found for this chapter")
    }

    // ----------------------- HELPERS -----------------------
    private fun okhttp3.Response.asJsoup(baseUrl: String? = null): Document {
        val html = this.body?.string().orEmpty()
        return if (baseUrl != null) Jsoup.parse(html, baseUrl) else Jsoup.parse(html)
    }
}
