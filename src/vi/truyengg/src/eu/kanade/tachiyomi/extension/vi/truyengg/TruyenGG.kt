package eu.kanade.tachiyomi.extension.vi.truyengg

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class TruyenGG : ParsedHttpSource(), ConfigurableSource {

    override val name = "TruyenGG"

    override val lang = "vi"

    private val defaultBaseUrl = "https://truyengg.com"

    override val supportsLatest = true

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    override fun headersBuilder(): Headers.Builder =
        super.headersBuilder().add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/truyen-moi-cap-nhat/trang-$page.html", headers)

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a.book_name")!!.attr("href"))
        title = element.select("a.book_name").text()
        thumbnail_url = element.selectFirst(".image-cover img")!!.attr("data-src")
    }

    override fun latestUpdatesSelector() = ".list_item_home .item_home"

    override fun latestUpdatesNextPageSelector() = ".pagination a.active + a"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/top-binh-chon/trang-$page.html", headers)

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun chapterListSelector() = "ul.list_chap > li.item_chap"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        name = element.select("a").text()
        date_upload = parseDate(element.select("span.cl99").text().trim())
    }

    private fun parseDate(date: String): Long = runCatching {
        dateFormat.parse(date)?.time
    }.getOrNull() ?: 0L

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("h1[itemprop=name]").text()
        author = document.selectFirst("p:contains(Tác Giả) + p")?.text()
        genre = document.select("a.clblue").joinToString { it.text() }
        description = document.select("div.story-detail-info").text().trim()
        thumbnail_url = document.selectFirst(".thumbblock img")!!.attr("abs:src")
        status = when (document.select("p:contains(Trạng Thái) + p").text()) {
            "Đang Cập Nhật" -> SManga.ONGOING
            "Hoàn Thành" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override fun pageListParse(document: Document): List<Page> =
        document.select(".content_detail img")
            .mapIndexed { idx, it ->
                Page(idx, imageUrl = it.attr("abs:src"))
            }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/tim-kiem/trang-$page.html".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
                .toString()
        } else {
            val builder = "$baseUrl/tim-kiem-nang-cao/trang-$page.html".toHttpUrl().newBuilder()
            (if (filters.isEmpty()) getFilterList() else filters).filterIsInstance<UriFilter>()
                .forEach { it.addToUri(builder) }
            builder.build().toString()
        }
        return GET(url, headers)
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Không dùng chung với tìm kiếm bằng tên"),
        CountryFilter(),
        StatusFilter(),
        ChapterCountFilter(),
        SortByFilter(),
        GenreList(getGenreList()),
    )

    interface UriFilter {
        fun addToUri(builder: HttpUrl.Builder)
    }

    open class UriPartFilter(
        name: String,
        private val query: String,
        private val vals: Array<Pair<String, String>>,
    ) : UriFilter, Filter.Select<String>(name, vals.map { it.first }.toTypedArray()) {
        override fun addToUri(builder: HttpUrl.Builder) {
            builder.addQueryParameter(query, vals[state].second)
        }
    }

    class CountryFilter : UriPartFilter(
        "Quốc gia",
        "country",
        arrayOf(
            "Tất cả" to "0",
            "Trung Quốc" to "1",
            "Việt Nam" to "2",
            "Hàn Quốc" to "3",
            "Nhật Bản" to "4",
            "Mỹ" to "5",
        ),
    )

    class StatusFilter : UriPartFilter(
        "Tình trạng",
        "status",
        arrayOf(
            "Tất cả" to "-1",
            "Đang tiến hành" to "0",
            "Hoàn thành" to "2",
        ),
    )

    class ChapterCountFilter : UriPartFilter(
        "Số lượng chương",
        "minchapter",
        arrayOf(
            "0" to "0",
            ">= 100" to "100",
            ">= 200" to "200",
            ">= 300" to "300",
            ">= 400" to "400",
            ">= 500" to "500",
        ),
    )

    class SortByFilter : UriFilter, Filter.Sort(
        "Sắp xếp",
        arrayOf("Ngày đăng", "Ngày cập nhật", "Lượt xem"),
        Selection(2, false),
    ) {
        override fun addToUri(builder: HttpUrl.Builder) {
            val index = state?.index ?: 2
            val ascending = if (state?.ascending == true) 1 else 0
            builder.addQueryParameter("sort", (index * 2 + ascending).toString())
        }
    }

    class Genre(name: String, val id: String) : Filter.TriState(name)

    class GenreList(state: List<Genre>) : UriFilter, Filter.Group<Genre>("Thể loại", state) {
        override fun addToUri(builder: HttpUrl.Builder) {
            val genres = mutableListOf<String>()
            val genresEx = mutableListOf<String>()

            state.forEach {
                when (it.state) {
                    TriState.STATE_INCLUDE -> genres.add(it.id)
                    TriState.STATE_EXCLUDE -> genresEx.add(it.id)
                    else -> {}
                }
            }

            builder.addQueryParameter("category", genres.joinToString(","))
            builder.addQueryParameter("notcategory", genresEx.joinToString(","))
        }
    }

    private fun getGenreList() = listOf(
        Genre("Action", "37"),
        Genre("Adventure", "38"),
        Genre("Anime", "39"),
        Genre("Cổ Đại", "40"),
        Genre("Comedy", "41"),
        Genre("Comic", "42"),
        Genre("Detective", "43"),
        Genre("Doujinshi", "44"),
        Genre("Drama", "45"),
        Genre("Ecchi", "80"),
        Genre("Fantasy", "46"),
        Genre("Gender Bender", "47"),
        Genre("Harem", "78"),
        Genre("Historical", "48"),
        Genre("Horror", "49"),
        Genre("Huyền Huyễn", "50"),
        Genre("Isekai", "51"),
        Genre("Josei", "52"),
        Genre("Magic", "53"),
        Genre("Manga", "81"),
        Genre("Manhua", "54"),
        Genre("Manhwa", "55"),
        Genre("Martial Arts", "56"),
        Genre("Mystery", "57"),
        Genre("Ngôn Tình", "58"),
        Genre("One shot", "59"),
        Genre("Psychological", "60"),
        Genre("Romance", "61"),
        Genre("School Life", "62"),
        Genre("Sci-fi", "63"),
        Genre("Seinen", "64"),
        Genre("Shoujo", "65"),
        Genre("Shoujo Ai", "66"),
        Genre("Shounen", "67"),
        Genre("Shounen Ai", "68"),
        Genre("Slice of life", "69"),
        Genre("Sports", "70"),
        Genre("Supernatural", "71"),
        Genre("Tragedy", "72"),
        Genre("Truyện Màu", "73"),
        Genre("Webtoon", "74"),
        Genre("Xuyên Không", "75"),
        Genre("Yuri", "76"),
    )

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

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
