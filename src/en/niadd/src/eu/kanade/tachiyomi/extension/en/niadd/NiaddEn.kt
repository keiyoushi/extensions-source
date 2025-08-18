package eu.kanade.tachiyomi.extension.en.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class NiaddEn : ParsedHttpSource() {

    override val name: String = "Niadd"
    override val baseUrl: String = "https://www.niadd.com"
    private val altBaseUrl: String = "https://www.nineanime.com"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    private fun defaultHeaders(): Headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36")
        .add("Referer", baseUrl)
        .build()

    // ===========================
    // Popular
    // ===========================
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/category/?page=$page", headers = defaultHeaders())

    override fun popularMangaSelector(): String = "div.manga-item:has(a[href*='/manga/'])"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val link = element.selectFirst("a[href*='/manga/']") ?: return manga

        manga.setUrlWithoutDomain(link.attr("href"))
        manga.title = element.selectFirst("div.manga-name")?.text()?.trim()
            ?: element.selectFirst("a[title]")?.attr("title")?.trim()
            ?: element.text().trim()

        val img = element.selectFirst("img")
        manga.thumbnail_url = img?.absUrl("src")
            ?: img?.absUrl("data-src")
            ?: img?.absUrl("data-original")

        manga.description = element.selectFirst("div.manga-intro")?.text()?.trim()

        return manga
    }

    override fun popularMangaNextPageSelector(): String? = "a.next"

    // ===========================
    // Latest
    // ===========================
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/list/New-Update/?page=$page", headers = defaultHeaders())

    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    // ===========================
    // Search
    // ===========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val q = URLEncoder.encode(query, "UTF-8")
        return GET("$baseUrl/search/?name=$q&page=$page", headers = defaultHeaders())
    }

    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    // ===========================
    // Details
    // ===========================
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.selectFirst("h1")?.text()?.trim() ?: ""

        val img = document.selectFirst("div.detail-cover img, .bookside-cover img")
        manga.thumbnail_url = img?.absUrl("src")
            ?: img?.absUrl("data-src")
            ?: img?.absUrl("data-original")

        manga.author = document.selectFirst("div[itemprop=author] span.bookside-bookinfo-value")
            ?.text()?.trim()
        manga.artist = manga.author

        manga.description = document.select("div.detail-section-box section.detail-synopsis")
            .text()?.trim()

        manga.genre = document.select("a span[itemprop=genre]").joinToString(", ") { it.text() }
        manga.status = SManga.UNKNOWN
        return manga
    }

    // ===========================
    // Chapters
    // ===========================
    override fun chapterListRequest(manga: SManga): Request {
        val chaptersUrl = if (manga.url.endsWith("/chapters.html")) manga.url
        else manga.url.removeSuffix("/") + "/chapters.html"
        return GET(baseUrl + chaptersUrl, headers = defaultHeaders())
    }

    override fun chapterListSelector(): String = "ul.chapter-list a.hover-underline"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val url = element.attr("href")

        chapter.setUrlWithoutDomain(url)
        chapter.name = element.selectFirst("span.chp-title")?.text()?.trim()
            ?: element.text().trim()

        val dateText = element.selectFirst("span.chp-time")?.text()
        chapter.date_upload = parseDate(dateText)
        return chapter
    }

    private fun parseDate(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        return try {
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH).parse(dateString)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // ===========================
    // Pages
    // ===========================
    override fun pageListRequest(chapter: SChapter): Request {
        val fullUrl = if (chapter.url.startsWith("http")) chapter.url else baseUrl + chapter.url
        val finalUrl = if (fullUrl.contains("nineanime.com")) fullUrl.replace("niadd.com", "nineanime.com") else fullUrl
        return GET(finalUrl, headers = defaultHeaders())
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        // Niadd
        val niaddImgs = document.select("div.pic_box img")
        if (niaddImgs.isNotEmpty()) {
            niaddImgs.forEachIndexed { i, img ->
                pages.add(Page(i, "", img.absUrl("src")))
            }
            return pages
        }

        // NineAnime
        val nineImgs = document.select("div.reader img[data-src]")
        if (nineImgs.isNotEmpty()) {
            nineImgs.forEachIndexed { i, img ->
                pages.add(Page(i, "", img.attr("data-src")))
            }
            return pages
        }

        // fallback: abre capítulo no navegador
        pages.add(Page(0, "", document.location()))
        return pages
    }

    override fun imageUrlParse(document: Document): String = ""
}
