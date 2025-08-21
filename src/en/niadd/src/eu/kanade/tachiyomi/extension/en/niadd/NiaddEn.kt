package eu.kanade.tachiyomi.extension.en.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class NiaddEn : ParsedHttpSource() {

    override val name: String = "Niadd"
    override val baseUrl: String = "https://www.niadd.com"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    // OkHttp sem delay
    override val client: OkHttpClient = network.client.newBuilder().build()

    // Headers custom
    private val customHeaders: Headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0")
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/category/?page=$page", headers = customHeaders)

    override fun popularMangaSelector(): String = "div.manga-item:has(a[href*='/manga/'])"

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

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/list/New-Update/?page=$page", headers = customHeaders)

    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val q = URLEncoder.encode(query, "UTF-8")
        return GET("$baseUrl/search/?name=$q&page=$page", headers = customHeaders)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.selectFirst("h1")?.text()?.trim() ?: ""
        val img = document.selectFirst("div.detail-cover img, .bookside-cover img")
        manga.thumbnail_url = img?.absUrl("src")?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("data-cfsrc")
            ?: img?.absUrl("data-src")
            ?: img?.absUrl("data-original")
        val author = document.selectFirst(
            "div.bookside-bookinfo div[itemprop=author] span.bookside-bookinfo-value",
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

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used.")
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val mangaUrl = if (manga.url.contains("?")) "${baseUrl}${manga.url}&waring=1"
                       else "${baseUrl}${manga.url}?waring=1"
        return GET(mangaUrl, headers = customHeaders)
    }

    override fun chapterListSelector(): String = "ul.detail-chlist li a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = element.absUrl("href")
        chapter.name = element.attr("title").ifBlank { element.text().trim() }

        // pages
        val dateText = element.parent()?.selectFirst("span.time")?.text()?.trim()
        chapter.date_upload = dateText?.let { parseChapterDate(it) } ?: 0L

        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            val formatter = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.ENGLISH)
            formatter.parse(date)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request = GET(chapter.url, headers = customHeaders)

    override fun pageListParse(document: Document): List<Page> {
        val directImgs = document.select("div.reader-area img")
        if (directImgs.isNotEmpty()) {
            val pages = mutableListOf<Page>()
            directImgs.forEachIndexed { i, img ->
                val url = img.absUrl("data-src").ifBlank { img.absUrl("src") }
                if (url.isNotBlank()) pages.add(Page(i, "", url))
            }
            if (pages.isNotEmpty()) return pages
        }

        val redirect = document.selectFirst("meta[http-equiv=refresh]")
            ?.attr("content")?.substringAfter("url=")?.trim()

        if (!redirect.isNullOrEmpty()) {
            client.newCall(GET(redirect, headers = customHeaders)).execute().use { resp ->
                val doc = resp.asJsoup(redirect)
                val imgs = doc.select("div.reader-area img")
                val pages = mutableListOf<Page>()
                imgs.forEachIndexed { i, img ->
                    val url = img.absUrl("data-src").ifBlank { img.absUrl("src") }
                    if (url.isNotBlank()) pages.add(Page(i, "", url))
                }
                if (pages.isNotEmpty()) return pages
            }
        }

        throw Exception("No images or redirect found for this chapter")
    }

    // Helpers
    private fun okhttp3.Response.asJsoup(baseUrl: String? = null): Document {
        val html = this.body?.string().orEmpty()
        return if (baseUrl != null) Jsoup.parse(html, baseUrl) else Jsoup.parse(html)
    }
}
