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
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = "a.next"

    // ---------- Latest / Recent Updates ----------
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/list/New-Update/?page=$page", headers)

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
        manga.title = document.selectFirst("h1")?.text()?.trim() ?: ""

        val img = document.selectFirst("div.detail-cover img, .bookside-cover img")
        manga.thumbnail_url = img?.absUrl("src")
            ?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("data-cfsrc")
            ?: img?.absUrl("data-src")
            ?: img?.absUrl("data-original")

        val author = document.selectFirst("div.bookside-bookinfo div[itemprop=author] span.bookside-bookinfo-value")
            ?.text()?.trim()
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

    // ---------- Chapters ----------
    override fun chapterListRequest(manga: SManga): Request {
        val chaptersUrl = if (manga.url.endsWith("/chapters.html")) manga.url else manga.url.removeSuffix("/") + "/chapters.html"
        return GET(baseUrl + chaptersUrl, headers)
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
        return chapter
    }

    private fun parseDate(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
            sdf.parse(dateString)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // ---------- Pages / Images ----------
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        // Images already in HTML
        val imgElements = document.select("div.pic_box img, img.manga_pic, div.reader-page img")
        imgElements.forEachIndexed { index, img ->
            val url = img.absUrl("src")
                .ifEmpty { img.absUrl("data-src") }
                .ifEmpty { img.absUrl("data-original") }
                .ifEmpty { img.absUrl("data-cfsrc") }

            if (url.isNotBlank()) pages.add(Page(index, "", url))
        }

        // Try to get images from JS
        if (pages.isEmpty()) {
            document.select("script").firstOrNull { it.data().contains("chp_url_pre") }?.data()?.let { js ->
                val regex = Regex("chp_url_pre\\s*=\\s*\"(.*?)\".*?url_end\\s*=\\s*\"(.*?)\".*?next_page\\s*=\\s*(\\d+)", RegexOption.DOT_MATCHES_ALL)
                regex.find(js)?.let { match ->
                    val base = match.groupValues[1]
                    val urlEnd = match.groupValues[2]
                    val nextPage = match.groupValues[3].toIntOrNull() ?: 1
                    for (i in 1..nextPage) {
                        pages.add(Page(i - 1, "", "$base$i$urlEnd"))
                    }
                }
            }
        }

        // Fallback using defalt -1.html, -2.html ...
        if (pages.isEmpty()) {
            val chapterBaseUrl = document.location().substringBeforeLast("-1.html")
            var pageIndex = 1
            while (true) {
                val pageUrl = "$chapterBaseUrl-$pageIndex.html"
                try {
                    val pageDoc = client.newCall(GET(pageUrl, headers)).execute().use { it.body?.string() }?.let { org.jsoup.Jsoup.parse(it) }
                        ?: break
                    val img = pageDoc.selectFirst("div.mangaread-img img, img.manga_pic, div.reader-page img")
                    val url = img?.absUrl("src")
                        ?.ifEmpty { img.absUrl("data-src") }
                        ?.ifEmpty { img.absUrl("data-original") }
                        ?.ifEmpty { img.absUrl("data-cfsrc") }
                        ?: break
                    pages.add(Page(pageIndex - 1, "", url))
                } catch (_: Exception) {
                    break
                }
                pageIndex++
            }
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used")
    }
}
