package eu.kanade.tachiyomi.extension.en.vyvymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class VyvyManga : ParsedHttpSource() {
    override val name = "VyvyManga"

    override val baseUrl = "https://vyvymanga.net"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MMM dd, yyy", Locale.US)

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/search" + if (page != 1) "?page=$page" else "", headers)

    override fun popularMangaSelector(): String =
        searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String =
        searchMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector(): String = ".comic-item"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst(".comic-title")!!.text()
        thumbnail_url = element.selectFirst(".comic-image")!!.absUrl("data-background-image")
    }

    override fun searchMangaNextPageSelector(): String = "[rel=next]"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/search?sort=updated_at" + if (page != 1) "&page=$page" else "", headers)

    override fun latestUpdatesSelector(): String =
        searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? =
        searchMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        artist = document.selectFirst(".pre-title:contains(Artist) ~ a")?.text()
        author = document.selectFirst(".pre-title:contains(Author) ~ a")?.text()
        description = document.selectFirst(".summary > .content")!!.text()
        genre = document.select(".pre-title:contains(Genres) ~ a").joinToString { it.text() }
        status = when (document.selectFirst(".pre-title:contains(Status) ~ span:not(.space)")?.text()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst(".img-manga")!!.absUrl("src")
    }

    // Chapters
    override fun chapterListSelector(): String =
        ".list-group > a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.absUrl("href")
        name = element.selectFirst("span")!!.text()
        date_upload = parseChapterDate(element.selectFirst("> p")?.text())
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request =
        GET(chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.d-block").mapIndexed { index, element ->
            Page(index, "", element.absUrl("data-src"))
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used.")
    }

    // Other
    // Date logic lifted from Madara
    private fun parseChapterDate(date: String?): Long {
        date ?: return 0

        fun SimpleDateFormat.tryParse(string: String): Long {
            return try {
                parse(string)?.time ?: 0
            } catch (_: ParseException) {
                0
            }
        }

        return when {
            "ago".endsWith(date) -> {
                parseRelativeDate(date)
            }
            else -> dateFormat.tryParse(date)
        }
    }

    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            date.contains("day") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("hour") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("minute") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.contains("second") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            else -> 0
        }
    }
}
