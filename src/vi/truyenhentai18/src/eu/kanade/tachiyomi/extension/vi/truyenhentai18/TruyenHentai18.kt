package eu.kanade.tachiyomi.extension.vi.truyenhentai18

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.util.Calendar

class TruyenHentai18 : HttpSource() {

    override val name = "Truyện Hentai 18+"

    override val baseUrl = "https://truyenhentai18.net"

    override val lang = "vi"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ======================================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/xem-nhieu-nhat" + if (page > 1) "/page/$page" else "", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.col-6.col-md-4.col-lg-2.mb-3").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                title = element.selectFirst("h2")!!.text()
                thumbnail_url = imageElement(element.selectFirst("img")!!)
            }
        }

        val hasNextPage = document.selectFirst("ul.pagination li a:contains(»)") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ======================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/moi-cap-nhat" + if (page > 1) "/page/$page" else "", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genreFilter = filters.filterIsInstance<GenreFilter>().firstOrNull()
        val selectedGenre = genreFilter?.toUriPart()
        return if (query.isBlank() && !selectedGenre.isNullOrEmpty()) {
            GET("$baseUrl/category/$selectedGenre" + if (page > 1) "/page/$page" else "", headers)
        } else {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .build()
            GET(url, headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Filters ======================================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Details ======================================

    override fun mangaDetailsParse(response: Response): SManga = response.asJsoup().run {
        SManga.create().apply {
            title = selectFirst("h1")!!.text()
            thumbnail_url = selectFirst("img.manga-cover")?.absUrl("src")
                ?: selectFirst(".card img.img-fluid")?.absUrl("src")

            genre = select("a.badge.bg-primary").joinToString { it.text() }

            select(".list-group-item, div").forEach { element ->
                val text = element.text()
                when {
                    text.contains("Trạng thái:", ignoreCase = true) -> {
                        status = when {
                            text.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
                            text.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
                            else -> SManga.UNKNOWN
                        }
                    }

                    text.contains("Tác giả:", ignoreCase = true) -> {
                        author = text.substringAfter(":").trim()
                    }
                }
            }

            description = select(".description").joinToString("\n") { it.wholeText().trim() }
        }
    }

    // ============================== Chapters ======================================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.chapter-item").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.fw-bold")!!.absUrl("href"))
                name = element.selectFirst("a.fw-bold")!!.text()
                val dateText = element.selectFirst("div.chapter-date")?.text()
                date_upload = dateText.toDate()
            }
        }
    }

    private fun String?.toDate(): Long {
        this ?: return 0L
        if (!this.contains("trước", ignoreCase = true)) {
            return 0L
        }

        return try {
            val calendar = Calendar.getInstance()

            val patterns = listOf(
                Regex("""(\d+)\s*giây""", RegexOption.IGNORE_CASE) to Calendar.SECOND,
                Regex("""(\d+)\s*phút""", RegexOption.IGNORE_CASE) to Calendar.MINUTE,
                Regex("""(\d+)\s*(giờ|tiếng)""", RegexOption.IGNORE_CASE) to Calendar.HOUR_OF_DAY,
                Regex("""(\d+)\s*ngày""", RegexOption.IGNORE_CASE) to Calendar.DAY_OF_MONTH,
                Regex("""(\d+)\s*tuần""", RegexOption.IGNORE_CASE) to Calendar.WEEK_OF_YEAR,
                Regex("""(\d+)\s*tháng""", RegexOption.IGNORE_CASE) to Calendar.MONTH,
                Regex("""(\d+)\s*năm""", RegexOption.IGNORE_CASE) to Calendar.YEAR,
            )

            for ((pattern, field) in patterns) {
                pattern.find(this)?.groupValues?.get(1)?.toIntOrNull()?.let { number ->
                    calendar.add(field, -number)
                    return calendar.timeInMillis
                }
            }

            0L
        } catch (_: Exception) {
            0L
        }
    }

    // ============================== Pages ======================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div#viewer.chapter-container img").mapIndexed { index, element ->
            Page(index, imageUrl = imageElement(element))
        }
    }

    private fun imageElement(element: Element): String? = when {
        element.hasAttr("data-src") -> element.attr("abs:data-src")
        else -> element.attr("abs:src")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
