package eu.kanade.tachiyomi.extension.id.pramramadhan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Pramramadhan : HttpSource() {

    override val name = "Pramramadhan"
    override val baseUrl = "https://01.pramramadhan.my.id"
    override val lang = "id"
    override val supportsLatest = true

    private val simpleDateFormat = SimpleDateFormat("d MMMM yyyy", Locale("id"))

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/search.php?sort=popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a.result-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.selectFirst("div.result-title")!!.text()
                thumbnail_url = element.selectFirst("div.result-cover img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    // ============================== Latest ================================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/search.php?sort=newest&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

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

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.series-title")!!.text()
            author = document.selectFirst(".tag-row:has(.tag-label:contains(Author)) .tag-pill")?.text()
            artist = document.selectFirst(".tag-row:has(.tag-label:contains(Artist)) .tag-pill")?.text()
            genre = document.select(".tag-row:has(.tag-label:contains(Genre)) .tag-list a.tag-pill")
                .joinToString { it.text() }
            status = parseStatus(document.selectFirst(".tag-row:has(.tag-label:contains(Status)) .tag-pill")?.text())
            description = document.selectFirst("p.series-desc")?.text()
            thumbnail_url = document.selectFirst("div.series-cover img")?.attr("abs:src")
        }
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.chapter-grid a.chapter-card").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                name = element.selectFirst("div.chapter-title")!!.text()
                val subTitle = element.selectFirst("div.chapter-sub")?.text()
                if (!subTitle.isNullOrEmpty()) {
                    name += " - $subTitle"
                }
                date_upload = parseChapterDate(element.selectFirst("div.chapter-time")?.text())
            }
        }
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
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.reader-container img.page").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

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
}
