package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

open class Niadd(
    override val name: String,
    override val baseUrl: String,
    private val langCode: String,
) : ParsedHttpSource() {

    override val lang: String = langCode
    override val supportsLatest: Boolean = true

    // ---------- Popular ----------

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/category/?page=$page", headers)

    override fun popularMangaSelector(): String =
        "div.manga-item:has(a[href*='/manga/'])"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val link = element.selectFirst("a[href*='/manga/']") ?: return manga
        manga.setUrlWithoutDomain(link.attr("href"))

        // Title fallback chain
        manga.title = element.selectFirst("div.manga-name")?.text()?.trim()
            ?: element.selectFirst("h3")?.text()?.trim()
            ?: link.attr("title")?.trim()
            ?: element.selectFirst("img")?.attr("alt")?.trim()
            ?: link.text().trim()

        // Thumbnail fallback chain
        val img = element.selectFirst("img")
        manga.thumbnail_url = img?.absUrl("data-original")
            ?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("data-src")
            ?: img?.absUrl("data-cfsrc")
            ?: img?.absUrl("src")

        manga.description = element.selectFirst("div.manga-intro")?.text()?.trim()
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = "a.next"

    // ---------- Latest ----------

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/category/last_update/?page=$page", headers)

    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    // ---------- Search ----------

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val q = URLEncoder.encode(query, "UTF-8")
        return GET("$baseUrl/search/?name=$q&page=$page", headers)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    // ---------- Details ----------

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        // Title
        manga.title = document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("div.detail-cover img")?.attr("alt")?.trim()
            ?: ""

        // Thumbnail
        val img = document.selectFirst("div.detail-cover img, .bookside-cover img")
        manga.thumbnail_url = img?.absUrl("data-original")
            ?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("data-src")
            ?: img?.absUrl("data-cfsrc")
            ?: img?.absUrl("src")

        // Author / Artist
        val author = document.selectFirst("div.bookside-bookinfo div[itemprop=author] span.bookside-bookinfo-value")
            ?.text()?.trim()
        manga.author = author
        manga.artist = author

        // Synopsis
        val synopsis = document.select("div.detail-section-box")
            .firstOrNull { it.selectFirst(".detail-cate-title")?.text()?.contains("Synopsis", true) == true }
            ?.selectFirst("section.detail-synopsis")
            ?.text()
            ?.trim()
            ?: ""

        // Alternate Names
        val alternatives = document.selectFirst("div.bookside-general-cell:contains(Alternative(s):)")
            ?.ownText()
            ?.replace("Alternative(s):", "")
            ?.trim()

        manga.description = buildString {
            append(synopsis)
            if (!alternatives.isNullOrBlank()) {
                append("\n\nAlternative(s): $alternatives")
            }
        }

        // Genres
        manga.genre = document.select("div.detail-section-box")
            .firstOrNull { it.selectFirst(".detail-cate-title")?.text()?.contains("Genres", true) == true }
            ?.select("section.detail-synopsis a span[itemprop=genre]")
            ?.joinToString(", ") { it.text().trim().trimStart(',') }
            ?: ""

        // Status
        manga.status = SManga.UNKNOWN

        return manga
    }

    // ---------- Chapters ----------

    override fun chapterListRequest(manga: SManga): Request {
        val chaptersUrl = if (manga.url.endsWith("/chapters.html")) {
            manga.url
        } else {
            manga.url.removeSuffix("/") + "/chapters.html"
        }
        return GET(baseUrl + chaptersUrl, headers)
    }

    override fun chapterListSelector(): String = "div.pic_box a" // Updated selector

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href"))

        // Chapter name fallback chain
        chapter.name = element.text().trim()
            .ifEmpty { element.attr("title").trim() }
            .ifEmpty { element.selectFirst("img")?.attr("alt")?.trim() ?: "" }

        chapter.date_upload = 0L // Niadd does not show upload dates reliably
        return chapter
    }

    // ---------- Pages / Images ----------

    override fun pageListParse(document: Document): List<Page> {
        // Each div.pic_box contains an <img>
        return document.select("div.mangaread-img div.pic_box img").mapIndexed { i, img ->
            val url = img.absUrl("data-original")
                .ifEmpty { img.absUrl("data-src") }
                .ifEmpty { img.absUrl("data-cfsrc") }
                .ifEmpty { img.absUrl("src") }
            Page(i, "", url)
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used")
    }

    // ---------- Date Parsing ----------

    private fun parseDate(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
            sdf.parse(dateString)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
