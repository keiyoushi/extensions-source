package eu.kanade.tachiyomi.extension.id.pramramadhan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Pramramadhan : ParsedHttpSource() {

    override val name = "Pramramadhan"
    override val baseUrl = "https://01.pramramadhan.my.id"
    override val lang = "id"
    override val supportsLatest = true

    private val simpleDateFormat = SimpleDateFormat("d MMMM yyyy", Locale("id"))

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/search.php?sort=popular&page=$page", headers)

    override fun popularMangaSelector(): String = "a.result-card"

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String? = null

    // ============================== Latest ================================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/search.php?sort=newest&page=$page", headers)

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    // ============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search.php".toHttpUrl().newBuilder()
        url.addQueryParameter("q", query)
        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.toUriPart())
                is GenreFilter -> url.addQueryParameter("genre", filter.toUriPart())
                is FormatFilter -> url.addQueryParameter("type", filter.toUriPart())
                is ProjectFilter -> url.addQueryParameter("project", filter.toUriPart())
                is StatusFilter -> url.addQueryParameter("status", filter.toUriPart())
                is AuthorFilter -> if (filter.state.isNotEmpty()) url.addQueryParameter("author", filter.state)
                is ArtistFilter -> if (filter.state.isNotEmpty()) url.addQueryParameter("artist", filter.state)
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaSelector(): String = "a.result-card"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst("div.result-title")!!.text()
        thumbnail_url = element.selectFirst("div.result-cover img")?.attr("abs:src")
    }

    override fun searchMangaNextPageSelector(): String? = null

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.series-title")!!.text()
        author = document.selectFirst(".tag-row:has(.tag-label:contains(Author)) .tag-pill")?.text()
        artist = document.selectFirst(".tag-row:has(.tag-label:contains(Artist)) .tag-pill")?.text()
        genre = document.select(".tag-row:has(.tag-label:contains(Genre)) .tag-list a.tag-pill")
            .joinToString { it.text() }
        status = parseStatus(document.selectFirst(".tag-row:has(.tag-label:contains(Status)) .tag-pill")?.text())
        description = document.selectFirst("p.series-desc")?.text()
        thumbnail_url = document.selectFirst("div.series-cover img")?.attr("abs:src")
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector(): String = "div.chapter-grid a.chapter-card"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.selectFirst("div.chapter-title")!!.text()
        val subTitle = element.selectFirst("div.chapter-sub")?.text()
        if (!subTitle.isNullOrEmpty()) {
            name += " - $subTitle"
        }
        date_upload = parseChapterDate(element.selectFirst("div.chapter-time")?.text())
    }

    private fun parseChapterDate(dateString: String?): Long {
        if (dateString == null) return 0L
        val trimmedDate = dateString.lowercase().replace("lalu", "").trim()
        val calendar = Calendar.getInstance()

        return try {
            val (value, unit) = trimmedDate.split(" ")
            val amount = value.toInt()
            when (unit) {
                "detik" -> calendar.apply { add(Calendar.SECOND, -amount) }.timeInMillis
                "menit" -> calendar.apply { add(Calendar.MINUTE, -amount) }.timeInMillis
                "jam" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -amount) }.timeInMillis
                "hari" -> calendar.apply { add(Calendar.DATE, -amount) }.timeInMillis
                "minggu" -> calendar.apply { add(Calendar.WEEK_OF_YEAR, -amount) }.timeInMillis
                "bulan" -> calendar.apply { add(Calendar.MONTH, -amount) }.timeInMillis
                "tahun" -> calendar.apply { add(Calendar.YEAR, -amount) }.timeInMillis
                else -> 0L
            }
        } catch (e: Exception) {
            simpleDateFormat.tryParse(dateString)
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> = document.select("div.reader-container img.page").mapIndexed { index, element ->
        Page(index, imageUrl = element.attr("abs:src"))
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used.")

    // ============================== Filters ===============================
    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        GenreFilter(),
        FormatFilter(),
        ProjectFilter(),
        StatusFilter(),
        AuthorFilter(),
        ArtistFilter(),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class SortFilter :
        UriPartFilter(
            "Urutkan",
            arrayOf(
                Pair("Populer", "popular"),
                Pair("Terbaru", "newest"),
                Pair("Terlama", "oldest"),
                Pair("Judul A-Z", "title_asc"),
                Pair("Judul Z-A", "title_desc"),
            ),
        )

    private class GenreFilter :
        UriPartFilter(
            "Genre",
            arrayOf(
                Pair("Semua", ""),
                Pair("Adventure", "Adventure"),
                Pair("Comedy", "Comedy"),
                Pair("Drama", "Drama"),
                Pair("Ecchi", "Ecchi"),
                Pair("Fantasy", "Fantasy"),
                Pair("Magic", "Magic"),
                Pair("Romance", "Romance"),
                Pair("School", "School"),
                Pair("Slice of Life", "Slice of Life"),
            ),
        )

    private class FormatFilter :
        UriPartFilter(
            "Format",
            arrayOf(
                Pair("Semua", ""),
                Pair("Light Novel", "Light Novel"),
                Pair("Manga", "Manga"),
                Pair("Web Novel", "Web Novel"),
            ),
        )

    private class ProjectFilter :
        UriPartFilter(
            "Project",
            arrayOf(
                Pair("Semua", ""),
                Pair("Continued", "continued"),
                Pair("Completed", "completed"),
                Pair("Dropped", "dropped"),
            ),
        )

    private class StatusFilter :
        UriPartFilter(
            "Status",
            arrayOf(
                Pair("Semua", ""),
                Pair("Ongoing", "ongoing"),
                Pair("Completed", "completed"),
            ),
        )

    private class AuthorFilter : Filter.Text("Author")
    private class ArtistFilter : Filter.Text("Artist")
}
