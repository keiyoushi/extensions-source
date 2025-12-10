package eu.kanade.tachiyomi.extension.id.Luvyaa

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Luvyaa : ParsedHttpSource() {

    override val name = "Luvyaa"
    override val baseUrl = "https://luvyaa.my.id"
    override val lang = "id"
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)

    // =========================================================================
    //  Popular
    // =========================================================================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga?page=$page&order=popular", headers)
    }

    override fun popularMangaSelector() = "div.bs"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val anchor = element.selectFirst("a")!!
        setUrlWithoutDomain(anchor.attr("href"))
        title = element.selectFirst("div.tt")?.text()?.trim() ?: anchor.text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun popularMangaNextPageSelector() = "a.next"

    // =========================================================================
    //  Latest
    // =========================================================================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga?page=$page&order=update", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // =========================================================================
    //  Search
    // =========================================================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/?s=$query", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // =========================================================================
    //  Manga Details
    // =========================================================================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.entry-title")?.text() ?: "Unknown"
        thumbnail_url = document.selectFirst("div.thumb img")?.attr("abs:src")
        description = document.selectFirst("div.entry-content")?.text()
        genre = document.select(".mgen a").joinToString { it.text() }

        // Updated to parse from div.imptdt structure
        author = document.selectFirst("div.imptdt:contains(Author) i")?.text()
        artist = document.selectFirst("div.imptdt:contains(Artist) i")?.text()

        val statusText = document.selectFirst("div.imptdt:contains(Status) i")?.text() ?: ""
        status = when {
            statusText.contains("Ongoing", true) -> SManga.ONGOING
            statusText.contains("Completed", true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // =========================================================================
    //  Chapter List
    // =========================================================================

    override fun chapterListSelector() = "div#chapterlist li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val anchor = element.selectFirst("a")!!
        setUrlWithoutDomain(anchor.attr("href"))

        name = element.selectFirst("span.chapternum")?.text() ?: anchor.text()

        val dateText = element.selectFirst("span.chapterdate")?.text() ?: ""
        date_upload = parseDate(dateText)
    }

    // =========================================================================
    //  Page List
    // =========================================================================

    override fun pageListParse(document: Document): List<Page> {
        val htmlImages = document.select("#readerarea img").toList().filter { it.attr("data-index") != "0" }

        if (htmlImages.isNotEmpty()) {
            return htmlImages.mapIndexed { index, img ->
                val url = img.attr("abs:data-src").ifBlank { img.attr("abs:src") }
                Page(index, "", url)
            }
        }

        val script = document.select("script:containsData(ts_reader.run)").firstOrNull()?.data()
            ?: return emptyList()
        val imageListString = Regex("""\"images\":\[(.*?)\]""").find(script)?.groupValues?.get(1)
            ?: return emptyList()
        return imageListString.split(",")
            .map { it.replace("\"", "").replace("\\/", "/") }
            .filter { it.isNotBlank() }
            .mapIndexed { index, url ->
                Page(index, "", url)
            }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    // =========================================================================
    //  Helpers
    // =========================================================================

    private fun parseDate(dateStr: String): Long {
        return try {
            if (dateStr.contains("ago", true)) {
                val number = Regex("""(\d+)""").find(dateStr)?.value?.toLong() ?: 0L
                val now = System.currentTimeMillis()
                return when {
                    "min" in dateStr -> now - (number * 60 * 1000)
                    "hour" in dateStr -> now - (number * 60 * 60 * 1000)
                    "day" in dateStr -> now - (number * 24 * 60 * 60 * 1000)
                    else -> 0L
                }
            }
            dateFormat.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
