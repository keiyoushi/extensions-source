package eu.kanade.tachiyomi.extension.vi.truyentranh3q

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class TruyenTranh3Q : HttpSource() {
    override val name: String = "TruyenTranh3Q"
    override val lang: String = "vi"
    override val baseUrl: String = "https://manhua3q.com"
    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ============================== Common ======================================

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.list_grid.grid > li").map { element ->
            SManga.create().apply {
                element.select("h3 a").let {
                    title = it.text()
                    setUrlWithoutDomain(it.attr("abs:href"))
                }
                thumbnail_url = element.selectFirst(".book_avatar a img")
                    ?.absUrl("src")
                    ?.let { url: String ->
                        url.toHttpUrlOrNull()
                            ?.queryParameter("url")
                            ?: url
                    }
            }
        }
        val hasNextPage = document.selectFirst(".page_redirect > a:last-child > p:not(.active)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Popular ======================================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/danh-sach/truyen-yeu-thich?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Latest ======================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/danh-sach/truyen-moi-cap-nhat?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Search ======================================

    private val searchUrl = "$baseUrl/tim-kiem-nang-cao"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = searchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("keyword", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.state.toString())
                is StatusFilter -> url.addQueryParameter("status", filter.state.toString())
                is CountryFilter -> url.addQueryParameter("country", filter.countryValues[filter.state])
                is MinChapterFilter -> url.addQueryParameter("minChap", filter.chapterValues[filter.state].toString())
                is GenreFilter -> {
                    val includeGenres = mutableListOf<String>()
                    val excludeGenres = mutableListOf<String>()
                    filter.state.forEach { genre ->
                        when (genre.state) {
                            Filter.TriState.STATE_INCLUDE -> includeGenres.add(genre.id.toString())
                            Filter.TriState.STATE_EXCLUDE -> excludeGenres.add(genre.id.toString())
                            else -> {}
                        }
                    }
                    if (includeGenres.isNotEmpty()) {
                        url.addQueryParameter("categories", includeGenres.joinToString(","))
                    }
                    if (excludeGenres.isNotEmpty()) {
                        url.addQueryParameter("nocategories", excludeGenres.joinToString(","))
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Details ======================================

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        document.selectFirst(".book_info > .book_other")?.also { info: Element ->
            title = info.select("h1[itemprop=name]").text()
            author = info.selectFirst("ul.list-info li.author p.col-xs-9")?.text()
            status = parseStatus(info.selectFirst("ul.list-info li.status p.col-xs-9")?.text())
            genre = info.select(".list01 li a").joinToString { it.text() }
        }
        description = document.select(".book_detail > .story-detail-info").joinToString { it.wholeText().trim() }
        thumbnail_url = document.selectFirst(".book_detail > .book_info > .book_avatar > img")
            ?.absUrl("src")?.let { url: String ->
                url.toHttpUrlOrNull()
                    ?.queryParameter("url")
                    ?: url
            }
    }

    private fun parseStatus(status: String?): Int {
        val ongoing = listOf("Đang Cập Nhật", "Đang Tiến Hành")
        val completed = listOf("Hoàn Thành", "Đã Hoàn Thành")
        val hiatus = listOf("Tạm ngưng", "Tạm hoãn")
        return when {
            status == null -> SManga.UNKNOWN
            ongoing.any { status.contains(it, ignoreCase = true) } -> SManga.ONGOING
            completed.any { status.contains(it, ignoreCase = true) } -> SManga.COMPLETED
            hiatus.any { status.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ======================================

    private fun parseDate(dateString: String): Long {
        val number = Regex("""(\d+)""").find(dateString)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            listOf("giây", "giây trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.SECOND, -number)
                cal.timeInMillis
            }
            listOf("phút", "phút trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.MINUTE, -number)
                cal.timeInMillis
            }
            listOf("giờ", "giờ trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.HOUR, -number)
                cal.timeInMillis
            }
            listOf("ngày", "ngày trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.DAY_OF_YEAR, -number)
                cal.timeInMillis
            }
            listOf("tuần", "tuần trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.WEEK_OF_YEAR, -number)
                cal.timeInMillis
            }
            listOf("tháng", "tháng trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.MONTH, -number)
                cal.timeInMillis
            }
            listOf("năm", "năm trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.YEAR, -number)
                cal.timeInMillis
            }
            else -> dateFormat.tryParse(dateString)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".works-chapter-list .works-chapter-item").map { element ->
            SChapter.create().apply {
                element.selectFirst(".name-chap > a")?.also { it: Element ->
                    name = it.text()
                    setUrlWithoutDomain(it.attr("abs:href"))
                }
                date_upload = parseDate(element.select(".time-chap").text())
            }
        }
    }

    // ============================== Pages ======================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".chapter_content .page-chapter img").mapIndexed { idx, it ->
            Page(idx, imageUrl = it.absUrl("data-src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ======================================

    class SortFilter(name: String, options: List<String>) : Filter.Select<String>(name, options.toTypedArray())
    class StatusFilter(name: String, options: List<String>) : Filter.Select<String>(name, options.toTypedArray())
    class CountryFilter(name: String, options: List<String>, val countryValues: List<String>) : Filter.Select<String>(name, options.toTypedArray())
    class MinChapterFilter(name: String, options: List<String>, val chapterValues: List<Int>) : Filter.Select<String>(name, options.toTypedArray())
    class Genre(name: String, val id: Int) : Filter.TriState(name)
    class GenreFilter(name: String, state: List<Genre>) : Filter.Group<Genre>(name, state)

    override fun getFilterList(): FilterList {
        fetchGenres()
        return FilterList(
            SortFilter("Sắp xếp", listOf("Ngày cập nhật", "Truyện mới", "Top all", "Top tháng", "Top tuần", "Top ngày", "Theo dõi", "Bình luận", "Số chapter")),
            StatusFilter("Trạng thái", listOf("Tất cả", "Đang Tiến Hành", "Hoàn Thành")),
            CountryFilter(
                "Quốc gia",
                listOf("Tất cả", "Nhật Bản", "Trung Quốc", "Hàn Quốc", "Khác"),
                listOf("all", "manga", "manhua", "manhwa", "other"),
            ),
            MinChapterFilter(
                "Số lượng chương",
                listOf(">=0 chapters", ">= 50 chapters", ">=100 chapters", ">=200 chapters", ">=300 chapters", ">=400 chapters", ">=500 chapters"),
                listOf(0, 50, 100, 200, 300, 400, 500),
            ),
            if (genreList.isEmpty()) {
                Filter.Header("Ấn 'Reset' để tải danh sách thể loại")
            } else {
                GenreFilter(
                    "Thể loại",
                    genreList.map { genre ->
                        Genre(genre.name, genre.id)
                    },
                )
            },
        )
    }

    private var genreList: List<Genre> = emptyList()
    private var fetchGenreAttempts: Int = 0

    private fun fetchGenres() {
        if (fetchGenreAttempts < 3 && genreList.isEmpty()) {
            scope.launch {
                try {
                    val document = client.newCall(GET(searchUrl, headers)).execute().asJsoup()
                    genreList = document.select(".genre-item").mapIndexed { index, element ->
                        Genre(element.text(), index + 1)
                    }
                } catch (_: Exception) {
                } finally {
                    fetchGenreAttempts++
                }
            }
        }
    }
}
