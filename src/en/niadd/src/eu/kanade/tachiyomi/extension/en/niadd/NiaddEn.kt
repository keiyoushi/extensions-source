package eu.kanade.tachiyomi.extension.en.niadd

import android.content.Context
import android.content.Intent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class NiaddEn : ParsedHttpSource() {

    override val name: String = "Niadd"
    override val baseUrl: String = "https://www.niadd.com"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.client.newBuilder().build()

    private val customHeaders: Headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0")
        .build()

    // Cloudflare Worker proxy
    private val proxyBase = "https://meu-worker.cloudflareworkers.com/?url="

    // --------------------- Popular ---------------------
    override fun popularMangaRequest(page: Int) =
        GET("$proxyBase${URLEncoder.encode("$baseUrl/category/?page=$page", "UTF-8")}", headers = customHeaders)

    override fun popularMangaSelector() = "div.manga-item:has(a[href*='/manga/'])"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val link = element.selectFirst("a[href*='/manga/']") ?: return manga
        manga.setUrlWithoutDomain(link.attr("href"))
        manga.title = element.selectFirst("div.manga-name")?.text()?.trim()
            ?: element.selectFirst("a[title]")?.attr("title")?.trim()
            ?: element.selectFirst("h3")?.text()?.trim()
            ?: element.selectFirst("img[alt]")?.attr("alt")?.trim()
            ?: link.text().trim()
        val img = element.selectFirst("img")
        manga.thumbnail_url = img?.absUrl("src")?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("data-cfsrc")
            ?: img?.absUrl("data-src")
            ?: img?.absUrl("data-original")
        manga.description = element.selectFirst("div.manga-intro")?.text()?.trim()
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = "a.next"

    // --------------------- Latest ---------------------
    override fun latestUpdatesRequest(page: Int) =
        GET("$proxyBase${URLEncoder.encode("$baseUrl/list/New-Update/?page=$page", "UTF-8")}", headers = customHeaders)

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    // --------------------- Search ---------------------
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): okhttp3.Request {
        val q = URLEncoder.encode(query, "UTF-8")
        return GET("$proxyBase${URLEncoder.encode("$baseUrl/search/?name=$q&page=$page", "UTF-8")}", headers = customHeaders)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    // --------------------- Details ---------------------
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.selectFirst("h1")?.text()?.trim() ?: ""
        val img = document.selectFirst("div.detail-cover img, .bookside-cover img")
        manga.thumbnail_url = img?.absUrl("src")?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("data-cfsrc")
            ?: img?.absUrl("data-src")
            ?: img?.absUrl("data-original")
        val author = document.selectFirst(
            "div.bookside-bookinfo div[itemprop=author] span.bookside-bookinfo-value"
        )?.text()?.trim()
        manga.author = author
        manga.artist = author
        val synopsis = document.select("div.detail-section-box")
            .firstOrNull { it.selectFirst(".detail-cate-title")?.text()?.contains("Synopsis", true) == true }
            ?.selectFirst("section.detail-synopsis")
            ?.text()?.trim() ?: ""
        val alternatives = document.selectFirst("div.bookside-general-cell:contains(Alternative(s):)")
            ?.ownText()?.replace("Alternative(s):", "")?.trim()
        manga.description = buildString {
            append(synopsis)
            if (!alternatives.isNullOrBlank()) append("\n\nAlternative(s): $alternatives")
        }
        manga.genre = document.select("div.detail-section-box")
            .firstOrNull { it.selectFirst(".detail-cate-title")?.text()?.contains("Genres", true) == true }
            ?.select("section.detail-synopsis a span[itemprop=genre]")
            ?.joinToString(", ") { it.text().trim().trimStart(',') } ?: ""
        manga.status = SManga.UNKNOWN
        return manga
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used.")

    // --------------------- Chapters ---------------------
    override fun chapterListRequest(manga: SManga): okhttp3.Request {
        val slug = manga.url.substringAfterLast("/").substringBefore(".html")
        val nineUrl = "https://www.nineanime.com/manga/$slug.html?waring=1"
        return GET("$proxyBase${URLEncoder.encode(nineUrl, "UTF-8")}", headers = customHeaders)
    }

    override fun chapterListSelector() = "ul.detail-chlist li a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = element.absUrl("href")
        chapter.name = element.attr("title").ifBlank { element.text().trim() }
        return chapter
    }

    // --------------------- Pages ---------------------
    override fun pageListRequest(chapter: SChapter) =
        GET("$proxyBase${URLEncoder.encode(chapter.url, "UTF-8")}", headers = customHeaders)

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        // 1️⃣ Tenta pegar imagens diretas
        val directImgs = document.select("div.reader-area img")
        if (directImgs.isNotEmpty()) {
            directImgs.forEachIndexed { i, img ->
                val url = img.absUrl("data-src").ifBlank { img.absUrl("src") }
                if (url.isNotBlank()) pages.add(Page(i, "", url))
            }
            if (pages.isNotEmpty()) return pages
        }

        // 2️⃣ Tenta JSON escondido
        val scriptText = document.select("script").joinToString("\n") { it.html() }
        val regex = Regex("all_imgs_url\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(scriptText)
        if (match != null) {
            val jsonText = "[" + match.groupValues[1].replace("'", "\"") + "]"
            try {
                val listType = object : TypeToken<List<String>>() {}.type
                val imgUrls: List<String> = Gson().fromJson(jsonText, listType)
                imgUrls.forEachIndexed { i, url ->
                    if (url.isNotBlank()) pages.add(Page(i, "", url))
                }
                if (pages.isNotEmpty()) return pages
            } catch (_: Exception) { /* continua */ }
        }

        // 3️⃣ Fallback WebView
        if (pages.isEmpty()) {
            pages.add(Page(0, "", chapter.url)) // será carregado via WebView
        }

        return pages
    }

    // --------------------- Fallback WebView ---------------------
    fun openChapterInWebView(context: Context, url: String) {
        val intent = Intent(context, NiaddWebViewActivity::class.java)
        intent.putExtra("url", url)
        context.startActivity(intent)
    }

    // --------------------- Helper ---------------------
    private fun okhttp3.Response.asJsoup(baseUrl: String? = null): Document {
        val html = this.body?.string().orEmpty()
        return if (baseUrl != null) Jsoup.parse(html, baseUrl) else Jsoup.parse(html)
    }
}
