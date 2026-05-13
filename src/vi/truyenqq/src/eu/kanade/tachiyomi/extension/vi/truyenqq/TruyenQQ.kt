package eu.kanade.tachiyomi.extension.vi.truyenqq

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class TruyenQQ :
    HttpSource(),
    ConfigurableSource {

    override val name: String = "TruyenQQ"

    override val lang: String = "vi"

    private val defaultBaseUrl = "https://truyenqqko.com"

    override val supportsLatest: Boolean = true

    private val preferences: SharedPreferences = getPreferences()

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 2, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    // Trang html chứa popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/truyen-yeu-thich" + if (page > 1) "/trang-$page" else "", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val manga = document.select("ul.grid > li").map { element ->
            SManga.create().apply {
                val anchor = element.selectFirst(".book_info .qtip a")!!
                setUrlWithoutDomain(anchor.attr("href"))
                title = anchor.text()
                thumbnail_url = element.selectFirst(".book_avatar img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst(".page_redirect > a:nth-last-child(2) > p:not(.active)") != null
        return MangasPage(manga, hasNextPage)
    }

    // Trang html chứa Latest (các cập nhật mới nhất)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/truyen-moi-cap-nhat" + if (page > 1) "/trang-$page" else "", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Tìm kiếm
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val endpoint = if (query.isNotBlank()) "tim-kiem" else "tim-kiem-nang-cao"
        val url = ("$baseUrl/$endpoint" + if (page > 1) "/trang-$page" else "").toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            } else {
                (filters.ifEmpty { getFilterList() }).forEach { filter ->
                    when (filter) {
                        is CountryFilter -> addQueryParameter("country", filter.values[filter.state].id)
                        is StatusFilter -> addQueryParameter("status", filter.values[filter.state].id)
                        is ChapterCountFilter -> addQueryParameter("minchapter", filter.values[filter.state].id)
                        is SortByFilter -> filter.state?.let {
                            addQueryParameter("sort", (it.index * 2 + if (it.ascending) 1 else 0).toString())
                        }
                        is GenreList -> {
                            addQueryParameter(
                                "category",
                                filter.state.filter { it.state == Filter.TriState.STATE_INCLUDE }
                                    .joinToString(",") { it.id },
                            )
                            addQueryParameter(
                                "notcategory",
                                filter.state.filter { it.state == Filter.TriState.STATE_EXCLUDE }
                                    .joinToString(",") { it.id },
                            )
                        }
                        else -> {}
                    }
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        val info = document.selectFirst(".list-info")!!

        title = document.select("h1").text()
        author = info.select(".org").joinToString { it.text() }
        genre = document.select(".list01 li").joinToString { it.text() }
        description = document.select(".story-detail-info")
            .joinToString("\n\n") { container ->
                val blocks = container.select("p")
                if (blocks.isNotEmpty()) blocks.joinToString("\n\n") { it.wholeText().trim() } else container.wholeText().trim()
            }

        thumbnail_url = document.selectFirst("img[itemprop=image]")?.absUrl("src")
        status = parseStatus(info.select(".status > p:last-child").text())
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        listOf("Đang Tiến Hành", "Đang Cập Nhật", "Đang Ra").any { status.contains(it, ignoreCase = true) } -> SManga.ONGOING
        listOf("Hoàn Thành", "Đã Hoàn Thành").any { status.contains(it, ignoreCase = true) } -> SManga.COMPLETED
        listOf("Tạm Ngưng", "Tạm Hoãn").any { status.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("div.works-chapter-list div.works-chapter-item").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                name = element.select("a").text().trim()
                date_upload = dateFormat.tryParse(element.select(".time-chap").text())
            }
        }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request = super.pageListRequest(chapter)
        .newBuilder()
        .cacheControl(CacheControl.FORCE_NETWORK)
        .build()

    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select(".page-chapter img:not([src*='stress.gif'])")
        .mapIndexed { idx, it ->
            Page(idx, imageUrl = it.absUrl("src"))
        }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Không dùng chung với tìm kiếm bằng tên"),
        CountryFilter(),
        StatusFilter(),
        ChapterCountFilter(),
        SortByFilter(),
        GenreList(getGenreList()),
    )

    private class Genre(name: String, val id: String) : Filter.TriState(name) {
        override fun toString(): String = name
    }

    private class CountryFilter :
        Filter.Select<Genre>(
            "Quốc gia",
            arrayOf(
                Genre("Tất cả", "0"),
                Genre("Trung Quốc", "1"),
                Genre("Việt Nam", "2"),
                Genre("Hàn Quốc", "3"),
                Genre("Nhật Bản", "4"),
                Genre("Mỹ", "5"),
            ),
        )

    private class StatusFilter :
        Filter.Select<Genre>(
            "Tình trạng",
            arrayOf(
                Genre("Tất cả", "-1"),
                Genre("Đang tiến hành", "0"),
                Genre("Hoàn thành", "2"),
            ),
        )

    private class ChapterCountFilter :
        Filter.Select<Genre>(
            "Số lượng chương",
            arrayOf(
                Genre("0", "0"),
                Genre(">= 100", "100"),
                Genre(">= 200", "200"),
                Genre(">= 300", "300"),
                Genre(">= 400", "400"),
                Genre(">= 500", "500"),
            ),
        )

    private class SortByFilter :
        Filter.Sort(
            "Sắp xếp",
            arrayOf("Ngày đăng", "Ngày cập nhật", "Lượt xem"),
            Selection(2, false),
        )

    private class GenreList(state: List<Genre>) : Filter.Group<Genre>("Thể loại", state)

    // console.log([...document.querySelectorAll(".genre-item")].map(e => `Genre("${e.innerText}", "${e.querySelector("span").dataset.id}")`).join(",\n"))
    private fun getGenreList() = listOf(
        Genre("Action", "26"),
        Genre("Adventure", "27"),
        Genre("Anime", "62"),
        Genre("Chuyển Sinh", "91"),
        Genre("Cổ Đại", "90"),
        Genre("Comedy", "28"),
        Genre("Comic", "60"),
        Genre("Demons", "99"),
        Genre("Detective", "100"),
        Genre("Doujinshi", "96"),
        Genre("Drama", "29"),
        Genre("Fantasy", "30"),
        Genre("Gender Bender", "45"),
        Genre("Harem", "47"),
        Genre("Historical", "51"),
        Genre("Horror", "44"),
        Genre("Huyền Huyễn", "468"),
        Genre("Isekai", "85"),
        Genre("Josei", "54"),
        Genre("Mafia", "69"),
        Genre("Magic", "58"),
        Genre("Manhua", "35"),
        Genre("Manhwa", "49"),
        Genre("Martial Arts", "41"),
        Genre("Military", "101"),
        Genre("Mystery", "39"),
        Genre("Ngôn Tình", "87"),
        Genre("One shot", "95"),
        Genre("Psychological", "40"),
        Genre("Romance", "36"),
        Genre("School Life", "37"),
        Genre("Sci-fi", "43"),
        Genre("Seinen", "42"),
        Genre("Shoujo", "38"),
        Genre("Shoujo Ai", "98"),
        Genre("Shounen", "31"),
        Genre("Shounen Ai", "86"),
        Genre("Slice of life", "46"),
        Genre("Sports", "57"),
        Genre("Supernatural", "32"),
        Genre("Tragedy", "52"),
        Genre("Trọng Sinh", "82"),
        Genre("Truyện Màu", "92"),
        Genre("Webtoon", "55"),
        Genre("Xuyên Không", "88"),
    )

    // Preferences
    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != defaultBaseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(defaultBaseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultBaseUrl"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                true
            }
        }.let(screen::addPreference)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

    companion object {
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng thay đổi."
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
    }
}
