package eu.kanade.tachiyomi.multisrc.bakamanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

abstract class BakaManga(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {
    override val supportsLatest = true

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/most-views/page/$page", headers)

    override fun popularMangaSelector(): String =
        ".li_truyen"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst(".name")!!.text()
        thumbnail_url = element.selectFirst("img")!!.absUrl("src")
    }

    override fun popularMangaNextPageSelector(): String? =
        ".page_redirect > a:last-child:not(.active)"

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty()) {
            val url = "$baseUrl/page/$page".toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
            GET(url.toString(), headers)
        } else {
            val filterList = if (filters.isEmpty()) getFilterList() else filters
            val genreFilter = filterList.find { it is GenreFilter } as GenreFilter
            val url = "$baseUrl/category/${genreFilter.toUriPart()}/page/$page"
            GET(url, headers)
        }
    }

    override fun searchMangaSelector(): String =
        popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? =
        popularMangaNextPageSelector()

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/latest-updates/page/$page", headers)

    override fun latestUpdatesSelector(): String =
        popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? =
        popularMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val info = document.selectFirst(".box_info")!!
        title = info.selectFirst("h1")!!.text()
        artist = info.select(".info-item:contains(Artist:) > a").joinToString { it.text() }

        val descElements = info.select(".story-detail-info:matchText")
        description = when {
            descElements.size > 2 -> {
                descElements.removeFirst() // "Summary:"
                descElements.removeLast() // "-From example.com"
                descElements.joinToString("\n") { it.text() }
            }
            else -> ""
        }

        val altTitles = info.selectFirst(".info-item:contains(Alternate Title:)")
            ?.text()
            ?.removePrefix("Alternate Title:")
            ?.trim()

        if (altTitles != null && altTitles.isNotEmpty()) {
            description += "\n\nAlt title(s): $altTitles"
        }

        genre = info.select(".post-categories > li > a").joinToString { it.text() }
        status = info.selectFirst(".info-item:contains(Status:)")!!.text()
            .removePrefix("Status:")
            .trim()
            .toStatus()

        thumbnail_url = info.selectFirst(".box_info img")!!.absUrl("src")
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> =
        super.chapterListParse(response).reversed()

    override fun chapterListSelector(): String =
        ".list-chapters > .list-chapters > .box_list > .chapter-item"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        name = element.selectFirst(".chap_name")!!.text()
        chapter_number = name
            .substringAfter(' ')
            .substringBefore(' ')
            .toFloatOrNull() ?: -1f

        date_upload = parseRelativeDate(element.selectFirst(".chap_update")!!.text())
    }

    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            date.contains("year") -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            date.contains("month") -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            date.contains("week") -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
            date.contains("day") -> cal.apply { add(Calendar.DAY_OF_YEAR, -number) }.timeInMillis
            date.contains("hour") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("minute") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.contains("second") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            else -> 0
        }
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("noscript > img").mapIndexed { i, img ->
            Page(i, document.location(), img.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document): String =
        ""

    // Filter
    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        GenreFilter(getGenreList()),
    )

    class GenreFilter(vals: Array<Pair<String, String>>) : UriPartFilter("Category", vals)

    abstract fun getGenreList(): Array<Pair<String, String>>

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // Other
    private fun String.toStatus() = when (this) {
        "Ongoing" -> SManga.ONGOING
        "Completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
