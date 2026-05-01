package eu.kanade.tachiyomi.extension.en.mangamob

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.Calendar

class Comivex : HttpSource() {

    override val id: Long = 954997815643613941
    override val name = "Comivex"
    override val baseUrl = "https://comivex.com"
    override val lang = "en"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/explore/?sort_by=Views&results=$page&ajax=1", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = Jsoup.parseBodyFragment(response.body.string(), baseUrl)

        val mangas = document.select("article.manga-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.card-cover")!!.attr("abs:href"))
                title = element.selectFirst(".card-title a")!!.text()
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/explore/?sort_by=Updated&results=$page&ajax=1", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/explore/".toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("search", query)
            }

            val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
            val sortFilter = filters.firstInstanceOrNull<SortFilter>()
            val statusFilter = filters.firstInstanceOrNull<StatusFilter>()

            addQueryParameter("genre_included", genreFilter?.selectedValue() ?: "")
            addQueryParameter("sort_by", sortFilter?.selectedValue() ?: "Views")
            addQueryParameter("status", statusFilter?.selectedValue() ?: "")
            addQueryParameter("results", page.toString())
            addQueryParameter("ajax", "1")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun getFilterList() = FilterList(
        GenreFilter(),
        SortFilter(),
        StatusFilter(),
    )

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".md-title")?.text() ?: throw Exception("Title not found")
            author = document.selectFirst(".md-author span")?.text()
            description = document.selectFirst("#synopsis")?.text()
            genre = document.select(".md-genres a.md-genre-pill").joinToString(", ") { it.text() }
            thumbnail_url = document.selectFirst(".md-cover-wrap img.md-cover")?.attr("abs:src")
            status = parseStatus(document.selectFirst(".md-status")?.text())
        }
    }

    private fun parseStatus(status: String?): Int {
        if (status == null) return SManga.UNKNOWN
        return when {
            status.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
            status.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
            status.contains("Hiatus", ignoreCase = true) -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".ch-list .ch-item").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.ch-link")!!.attr("abs:href"))
                name = element.selectFirst(".ch-num")?.text() ?: ""
                date_upload = parseRelativeDate(element.selectFirst(".ch-date")?.text() ?: "")
            }
        }
    }

    private fun parseRelativeDate(dateStr: String): Long {
        val now = Calendar.getInstance()
        var matched = false

        RELATIVE_DATE_REGEX.findAll(dateStr).forEach { match ->
            matched = true
            val amount = match.groupValues[1].toInt()
            val unit = match.groupValues[2]

            when (unit) {
                "year" -> now.add(Calendar.YEAR, -amount)
                "month" -> now.add(Calendar.MONTH, -amount)
                "week" -> now.add(Calendar.WEEK_OF_YEAR, -amount)
                "day" -> now.add(Calendar.DAY_OF_YEAR, -amount)
                "hour" -> now.add(Calendar.HOUR_OF_DAY, -amount)
                "minute" -> now.add(Calendar.MINUTE, -amount)
            }
        }
        return if (matched) now.timeInMillis else 0L
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("#chapter-images .page-wrapper img").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s+(year|month|week|day|hour|minute)s?""")
    }
}
