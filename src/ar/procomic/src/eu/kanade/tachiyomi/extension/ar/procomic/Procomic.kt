package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class Procomic : ParsedHttpSource() {

    override val name = "Procomic"
    override val baseUrl = "https://procomic.pro/"
    override val lang = "ar"
    override val supportsLatest = true
    override val client = network.cloudflareClient

    // Preferences
    private val preferences by lazy {
        sourcePref.getSharedPreferences("source_${id}_prefs", 0)
    }

    var customBaseUrl: String
        get() = preferences.getString("custom_base_url", baseUrl) ?: baseUrl
        set(value) = preferences.edit().putString("custom_base_url", value).apply()

    var hidePaidChapters: Boolean
        get() = preferences.getBoolean("hide_paid_chapters", true)
        set(value) = preferences.edit().putBoolean("hide_paid_chapters", value).apply()

    var safeBrowsing: Boolean
        get() = preferences.getBoolean("safe_browsing", true)
        set(value) = preferences.edit().putBoolean("safe_browsing", value).apply()

    // Latest Updates
    override fun latestUpdatesRequest(page: Int) = GET("$customBaseUrl/updates?page=$page", headers)

    override fun latestUpdatesSelector() = "div.manga-item, article.comic-card, div.comic-box"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a.manga-link, a.comic-link, h3 a").first()?.attr("href") ?: "")
        title = element.select("h3.manga-title, h3.comic-title, .comic-name").text()
        thumbnail_url = element.select("img.manga-cover, img.comic-thumb, img").first()?.attr("src") ?: ""
        description = element.select("p.description, .comic-desc").text()
    }

    override fun latestUpdatesNextPageSelector() = "a.next-page, a[rel=next], .pagination a:last-child"

    // Popular Manga
    override fun popularMangaRequest(page: Int) = GET("$customBaseUrl/popular?page=$page", headers)

    override fun popularMangaSelector() = "div.manga-item, article.comic-card, div.comic-box"

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = "a.next-page, a[rel=next], .pagination a:last-child"

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$customBaseUrl/search?q=${query.replace(" ", "+")}&page=$page", headers)
    }

    override fun searchMangaSelector() = "div.manga-item, article.comic-card, div.comic-box"

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = "a.next-page, a[rel=next], .pagination a:last-child"

    // Manga Details
    override fun mangaDetailsRequest(manga: SManga) = GET(customBaseUrl + manga.url, headers)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h1.manga-title, h1.comic-title, .series-name").text()
        author = document.select("div.author, span.author-name, .manga-author").text()
        artist = document.select("div.artist, span.artist-name").text()
        description = document.select("div.synopsis, div.description, .manga-description").text()
        thumbnail_url = document.select("img.manga-cover, img.comic-poster, .series-cover").first()?.attr("src") ?: ""
        
        status = when {
            document.select("span.status").text().contains("مستمر", ignoreCase = true) -> SManga.ONGOING
            document.select("span.status").text().contains("مكتمل", ignoreCase = true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val genres = document.select("a.genre-tag, span.genre, .manga-genre").map { it.text() }
        genre = genres.joinToString(", ")
    }

    // Chapters
    override fun chapterListRequest(manga: SManga) = GET(customBaseUrl + manga.url, headers)

    override fun chapterListSelector() = "div.chapter-item, li.chapter-list-item, .chapter-row"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a.chapter-link, a.chapter-url").first()?.attr("href") ?: "")
        name = element.select("span.chapter-num, .chapter-name, .chapter-title").text()
        date_upload = parseDate(element.select("span.chapter-date, time, .chapter-time").attr("datetime"))

        // Hide paid chapters if enabled
        if (hidePaidChapters && element.select(".paid-badge, .locked-chapter, .premium-tag").isNotEmpty()) {
            return@apply
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(chapterListSelector()).map { chapterFromElement(it) }
        
        return if (hidePaidChapters) {
            chapters.filter { it.name.isNotEmpty() }
        } else {
            chapters
        }
    }

    // Page List
    override fun pageListRequest(chapter: SChapter) = GET(customBaseUrl + chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var pageIndex = 0

        document.select("img.comic-page, img.chapter-image, img.reader-image, div.reader-image img").forEach { img ->
            val imageUrl = img.attr("src").ifEmpty { img.attr("data-src") }
            if (imageUrl.isNotEmpty()) {
                pages.add(Page(pageIndex++, "", imageUrl))
            }
        }

        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    // Image URL Request with Referer
    override fun imageRequest(page: Page): Request {
        return Request.Builder()
            .url(page.imageUrl!!)
            .header("Referer", customBaseUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
    }

    // Helper Functions
    private fun parseDate(dateString: String): Long {
        return if (dateString.isNotEmpty()) {
            try {
                val format = SimpleDateFormat("yyyy-MM-dd", Locale("ar"))
                format.parse(dateString)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        } else {
            0L
        }
    }

    override fun getFilterList() = FilterList()
}
