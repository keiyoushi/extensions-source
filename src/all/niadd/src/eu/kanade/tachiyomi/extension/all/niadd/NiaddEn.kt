package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class NiaddEn : ParsedHttpSource() {

    override val name: String = "Niadd English"
    override val baseUrl: String = "https://www.niadd.com"
    private val langCode: String = "en"

    override val lang: String = langCode
    override val supportsLatest: Boolean = true

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/category/?page=$page"
        println("🔍 [NiaddEn] Popular request: $url")
        return GET(url, headers)
    }

    override fun popularMangaSelector(): String =
        "div.manga-item:has(a[href*='/manga/'])"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val link = element.selectFirst("a[href*='/manga/']")
        if (link == null) {
            println("⚠️ [NiaddEn] No link found in popular element.")
            return manga
        }
        manga.setUrlWithoutDomain(link.attr("href"))

        manga.title = element.selectFirst("div.manga-name")?.text()?.trim()
            ?: element.selectFirst("a[title]")?.attr("title")?.trim()
            ?: element.selectFirst("h3")?.text()?.trim()
            ?: element.selectFirst("img[alt]")?.attr("alt")?.trim()
            ?: link.text().trim()

        val img = element.selectFirst("img")
        manga.thumbnail_url = img?.absUrl("src")
            ?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("data-cfsrc")
            ?: img?.absUrl("data-src")
            ?: img?.absUrl("data-original")

        manga.description = element.selectFirst("div.manga-intro")?.text()?.trim()

        println("✅ [NiaddEn] Popular manga parsed: ${manga.title} | ${manga.url}")
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = "a.next"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/list/New-Update/?page=$page"
        println("🔍 [NiaddEn] Latest updates request: $url")
        return GET(url, headers)
    }

    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/search/?name=$q&page=$page"
        println("🔍 [NiaddEn] Search request: $url")
        return GET(url, headers)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.selectFirst("h1")?.text()?.trim() ?: ""
        println("📖 [NiaddEn] Parsing details for: ${manga.title}")

        val img = document.selectFirst("div.detail-cover img, .bookside-cover img")
        manga.thumbnail_url = img?.absUrl("src")
            ?.takeIf { it.isNotBlank() }
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

        println("✅ [NiaddEn] Details parsed: ${manga.title} | Author: ${manga.author}")
        return manga
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val chaptersUrl =
            if (manga.url.endsWith("/chapters.html")) manga.url else manga.url.removeSuffix("/") + "/chapters.html"
        val fullUrl = baseUrl + chaptersUrl
        println("🔍 [NiaddEn] Chapter list request: $fullUrl")
        return GET(fullUrl, headers)
    }

    override fun chapterListSelector(): String = "ul.chapter-list a.hover-underline"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href"))

        chapter.name = element.selectFirst("span.chp-title")?.text()?.trim()
            ?: element.attr("title")?.trim()
            ?: element.text().trim()

        val dateText = element.selectFirst("span.chp-time")?.text()
        chapter.date_upload = parseDate(dateText)

        println("📌 [NiaddEn] Chapter parsed: ${chapter.name} | Date: $dateText")
        return chapter
    }

    private fun parseDate(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
            sdf.parse(dateString)?.time ?: 0L
        } catch (e: Exception) {
            println("⚠️ [NiaddEn] Failed to parse date: $dateString")
            0L
        }
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val pageOptions = document.select("select.sl-page option").map { it.attr("value") }
        println("🔍 [NiaddEn] Found ${pageOptions.size} page options.")

        val regex = Regex(""".*-(\d+)-(\d+)\.html""")

        val sortedUrls = pageOptions.sortedWith(compareBy { url ->
            val match = regex.find(url)
            val lote = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val page = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
            lote * 1000 + page
        })

        var pageIndex = 0
        sortedUrls.forEach { url ->
            println("➡️ [NiaddEn] Fetching page URL: $url")
            val pageDoc = client.newCall(GET(url, headers)).execute().asJsoup()
            pageDoc.select("img.manga_pic").forEach { img ->
                val imageUrl = img.absUrl("src")
                    .ifBlank { img.absUrl("data-src") }
                    .ifBlank { img.absUrl("data-original") }
                pages.add(Page(pageIndex++, "", imageUrl))
                println("🖼️ [NiaddEn] Page added: $imageUrl")
            }
        }

        println("✅ [NiaddEn] Total pages parsed: ${pages.size}")
        return pages
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used")
    }
}
