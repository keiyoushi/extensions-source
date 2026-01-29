package eu.kanade.tachiyomi.extension.vi.truyentranh3q

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TruyenTranh3Q : ParsedHttpSource() {
    override val name: String = "TruyenTranh3Q"
    override val lang: String = "vi"
    override val baseUrl: String = "https://manhua3q.com"
    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().add("Referer", "$baseUrl/")
    }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/danh-sach/truyen-yeu-thich?page=$page", headers)
    }

    override fun popularMangaSelector(): String = "ul.list_grid.grid > li"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            thumbnail_url = element.selectFirst(".book_avatar a img")
                ?.absUrl("src")
                ?.let { url ->
                    url.toHttpUrlOrNull()
                        ?.queryParameter("url")
                        ?: url
                }
        }
    }

    override fun popularMangaNextPageSelector(): String = ".page_redirect > a:last-child > p:not(.active)"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/danh-sach/truyen-moi-cap-nhat?page=$page", headers)
    }

    // same as popularManga
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    // search
    private val searchUrl = "$baseUrl/tim-kiem-nang-cao"
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = searchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        // always add search query if present
        if (query.isNotBlank()) {
            url.addQueryParameter("keyword", query)
        }

        // process filters regardless of search query
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
                            else -> {} // do nothing for STATE_IGNORE
                        }
                    }
                    if (includeGenres.isNotEmpty()) {
                        url.addQueryParameter("categories", includeGenres.joinToString(","))
                    }
                    if (excludeGenres.isNotEmpty()) {
                        url.addQueryParameter("nocategories", excludeGenres.joinToString(","))
                    }
                }
                else -> {} // do nothing for unhandled filters
            }
        }

        return GET(url.build(), headers)
    }

    // same as popularManga
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.selectFirst(".book_info > .book_other")?.let { info ->
                title = info.select("h1[itemprop=name]").text()
                author = info.selectFirst("ul.list-info li.author p.col-xs-9")?.text()
                status = parseStatus(info.selectFirst("ul.list-info li.status p.col-xs-9")?.text())
                genre = info.select(".list01 li a").joinToString { it.text() }
            }
            description = document.select(".book_detail > .story-detail-info").joinToString { it.wholeText().trim() }
            thumbnail_url = document.selectFirst(".book_detail > .book_info > .book_avatar > img")?.absUrl("abs:src")
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

    // chapters
    override fun chapterListSelector(): String = ".works-chapter-list .works-chapter-item"
    private fun parseDate(dateString: String): Long {
        val number = Regex("""(\d+)""").find(dateString)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            listOf("giây", "giây trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.SECOND, -number); cal.timeInMillis
            }
            listOf("phút", "phút trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.MINUTE, -number); cal.timeInMillis
            }
            listOf("giờ", "giờ trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.HOUR, -number); cal.timeInMillis
            }
            listOf("ngày", "ngày trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.DAY_OF_YEAR, -number); cal.timeInMillis
            }
            listOf("tuần", "tuần trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.WEEK_OF_YEAR, -number); cal.timeInMillis
            }
            listOf("tháng", "tháng trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.MONTH, -number); cal.timeInMillis
            }
            listOf("năm", "năm trước").any { dateString.contains(it, ignoreCase = true) } -> {
                cal.add(Calendar.YEAR, -number); cal.timeInMillis
            }
            else -> dateFormat.tryParse(dateString)
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.selectFirst(".name-chap > a")?.let {
                name = it.text()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            date_upload = parseDate(element.select(".time-chap").text())
        }
    }

    // parse pages
    private val pageListSelector = ".chapter_content .page-chapter img"

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListSelector).mapIndexed { idx, it ->
            Page(idx, imageUrl = it.absUrl("data-src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // filters
    class SortFilter(name: String, options: List<String>) : Filter.Select<String>(name, options.toTypedArray())
    class StatusFilter(name: String, options: List<String>) : Filter.Select<String>(name, options.toTypedArray())
    class CountryFilter(name: String, options: List<String>, val countryValues: List<String>) : Filter.Select<String>(name, options.toTypedArray())
    class MinChapterFilter(name: String, options: List<String>, val chapterValues: List<Int>) : Filter.Select<String>(name, options.toTypedArray())
    class Genre(name: String, val id: Int) : Filter.TriState(name)
    class GenreFilter(name: String, state: List<Genre>) : Filter.Group<Genre>(name, state)

    private val scope = CoroutineScope(Dispatchers.IO)
    private fun launchIO(block: () -> Unit) = scope.launch { block() }

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }
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

    private fun genresRequest() = GET(searchUrl, headers)

    private fun parseGenres(document: Document): List<Genre> {
        return document.select(".genre-item").mapIndexed { index, element ->
            Genre(element.text(), index + 1)
        }
    }

    private fun fetchGenres() {
        if (fetchGenreAttempts < 3 && genreList.isEmpty()) {
            try {
                genreList = client.newCall(genresRequest()).execute().asJsoup().let(::parseGenres)
            } catch (_: Exception) {
            } finally {
                fetchGenreAttempts++
            }
        }
    }
}
