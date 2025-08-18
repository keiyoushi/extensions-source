package eu.kanade.tachiyomi.extension.en.niadd

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

    override val name: String = "Niadd"
    override val baseUrl: String = "https://www.niadd.com"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        GET(
            "$baseUrl/category/?page=$page",
            headers,
        )

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

    override fun popularMangaNextPageSelector(): String? =
        "a.next"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET(
            "$baseUrl/list/New-Update/?page=$page",
            headers,
        )

    override fun latestUpdatesSelector(): String =
        popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? =
        popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val q = URLEncoder.encode(query, "UTF-8")
        return GET(
            "$baseUrl/search/?name=$q&page=$page",
            headers,
        )
    }

    override fun searchMangaSelector(): String =
        popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? =
        popularMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        manga.title = document.selectFirst("h1")?.text()?.trim() ?: ""

        val img = document.selectFirst("div.detail-cover img, .bookside-cover img")
        manga.thumbnail_url = img?.absUrl("src")
            ?.takeIf { it.isNotBlank() }
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
            ?.text()?.trim()
            ?: ""

        val alternatives = document.selectFirst("div.bookside-general-cell:contains(Alternative(s):)")
            ?.ownText()?.replace("Alternative(s):", "")?.trim()

        manga.description = buildString {
            append(synopsis)
            if (!alternatives.isNullOrBlank()) {
                append("\n\nAlternative(s): $alternatives")
            }
        }

        manga.genre = document.select("div.detail-section-box")
            .firstOrNull { it.selectFirst(".detail-cate-title")?.text()?.contains("Genres", true) == true }
            ?.select("section.detail-synopsis a span[itemprop=genre]")
            ?.joinToString(", ") { it.text().trim().trimStart(',') }
            ?: ""

        manga.status = SManga.UNKNOWN

        return manga
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val chaptersUrl = if (manga.url.endsWith("/chapters.html")) {
            manga.url
        } else {
            manga.url.removeSuffix("/") + "/chapters.html"
        }
        return GET(
            baseUrl + chaptersUrl,
            headers,
        )
    }

    override fun chapterListSelector(): String =
        "ul.chapter-list a.hover-underline"

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
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH).parse(dateString)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("section.mangaread-img img.manga_pic").forEachIndexed { index, img ->
            val imageUrl = img.absUrl("src")
                .ifBlank { img.absUrl("data-src") }
                .ifBlank { img.absUrl("data-original") }
            pages.add(Page(index, "", imageUrl))
        }

        val chapterUrl = document.location()
        val regex = Regex("""(.+)-(\d+)\.html""")
        val match = regex.find(chapterUrl)

        if (match != null) {
            val base = match.groupValues[1]
            val totalPages = match.groupValues[2].toIntOrNull() ?: pages.size

            val headersWithReferer = headersBuilder()
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36")
                .add("Referer", chapterUrl)
                .build()

            for (i in 2..totalPages) {
                val pageUrl = "$base-$i.html"
                val pageDoc = client.newCall(GET(pageUrl, headersWithReferer)).execute().asJsoup()

                pageDoc.select("section.mangaread-img img.manga_pic").forEach { img ->
                    val imageUrl = img.absUrl("src")
                        .ifBlank { img.absUrl("data-src") }
                        .ifBlank { img.absUrl("data-original") }
                    pages.add(Page(pages.size, "", imageUrl))
                }
            }
        }

        return pages
    }


    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used")
    }
}
