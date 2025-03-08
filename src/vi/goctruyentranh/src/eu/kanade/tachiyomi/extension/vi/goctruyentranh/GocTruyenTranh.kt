package eu.kanade.tachiyomi.extension.vi.goctruyentranh

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.getPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GocTruyenTranh : ParsedHttpSource(), ConfigurableSource {

    override val lang = "vi"

    private val defaultBaseUrl = "https://goctruyentranh.net"

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val name = "GocTruyenTranh"

    override val supportsLatest = true

    private val searchUrl by lazy { "${getPrefBaseUrl()}/baseapi/comics/filterComic" }

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US)

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/danh-sach/truyen-moi-cap-nhat?page=$page", headers)

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst("a.line-clamp-2").let {
            setUrlWithoutDomain(it!!.absUrl("href"))
            title = it.text()
        }
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun latestUpdatesNextPageSelector(): String = "nav ul li"

    override fun latestUpdatesSelector(): String = "section.mt-12 > .grid > .flex"

    override fun popularMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector(): String = latestUpdatesNextPageSelector()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/danh-sach/truyen-hot?page=$page", headers)

    override fun popularMangaSelector(): String = latestUpdatesSelector()

    override fun chapterListSelector(): String = "section ul li a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("a").let {
            setUrlWithoutDomain(it!!.absUrl("href"))
            name = it.select(".items-center:contains(Chapter)").text()
            date_upload = parseDate(it.select(".text-center").text())
        }
    }

    private fun parseDate(date: String): Long = runCatching {
        val calendar = Calendar.getInstance()
        val number = date.replace(Regex("[^0-9]"), "").trim().toInt()
        when (date.replace(Regex("[0-9]"), "").trim()) {
            "phút trước" -> calendar.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            "giờ trước" -> calendar.apply { add(Calendar.HOUR, -number) }.timeInMillis
            "ngày trước" -> calendar.apply { add(Calendar.DAY_OF_YEAR, -number) }.timeInMillis
            else -> dateFormat.parse(date)?.time
        }
    }.getOrNull() ?: 0L

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("section aside:first-child h1")!!.text()
        genre = document.select("span:contains(Thể loại:) ~ a").joinToString { it.text().trim(',', ' ') }
        description = document.selectFirst("div.mt-3")?.text()
        thumbnail_url = document.selectFirst("section aside:first-child img")?.absUrl("src")
        status = parseStatus(document.selectFirst("span:contains(Trạng thái:) + b")?.text())
        author = document.select("span:contains(Tác giả:) + b").joinToString { it.text() }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        status.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun pageListParse(document: Document): List<Page> = document.select("img.lozad").mapIndexed { i, e ->
        Page(i, imageUrl = e.absUrl("data-src"))
    }

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector(): String = throw UnsupportedOperationException()
    override fun searchMangaSelector(): String = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = searchUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", query)
            addQueryParameter("page", page.toString())

            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is TextField -> setQueryParameter(filter.key, filter.state)
                    is GenreList ->
                        filter.state
                            .filter { it.state }
                            .map { it.id }
                            .forEach { addQueryParameter("categories", it) }
                    is StatusList ->
                        filter.state
                            .filter { it.state }
                            .map { it.id }
                            .forEach { addQueryParameter("status", it) }
                    is ChapterCountList ->
                        {
                            val chapterCount = getChapterCountList()[filter.state]
                            addQueryParameter("minChap", chapterCount.id)
                        }
                    is SortByList ->
                        {
                            val sort = getSortByList()[filter.state]
                            addQueryParameter("sort", sort.id)
                        }
                    is CountryList ->
                        filter.state
                            .filter { it.state }
                            .map { it.id }
                            .forEach { addQueryParameter("country", it) }

                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val json = json.decodeFromString<SearchDTO>(response.body.string())
        val manga = json.comics.data.map {
            SManga.create().apply {
                title = it.name
                thumbnail_url = it.thumbnail
                setUrlWithoutDomain("$baseUrl/" + it.slug)
            }
        }
        val hasNextPage = json.comics.current_page != json.comics.last_page
        return MangasPage(manga, hasNextPage)
    }

    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)
    private class Genre(name: String, val id: String) : Filter.CheckBox(name)

    private class StatusList(status: List<Status>) : Filter.Group<Status>("Trạng Thái", status)
    private class Status(name: String, val id: String) : Filter.CheckBox(name)

    private class ChapterCountList(chapter: Array<ChapterCount>) : Filter.Select<ChapterCount>("Độ dài", chapter)
    private class ChapterCount(name: String, val id: String) : Filter.CheckBox(name) {
        override fun toString(): String {
            return name
        }
    }

    private class SortByList(sort: Array<SortBy>) : Filter.Select<SortBy>("Sắp xếp", sort)
    private class SortBy(name: String, val id: String) : Filter.CheckBox(name) {
        override fun toString(): String {
            return name
        }
    }

    private class CountryList(country: List<Country>) : Filter.Group<Country>("Quốc gia", country)
    private class Country(name: String, val id: String) : Filter.CheckBox(name)

    override fun getFilterList() = FilterList(
        GenreList(getGenreList()),
        StatusList(getStatusList()),
        ChapterCountList(getChapterCountList()),
        SortByList(getSortByList()),
        CountryList(getCountryList()),
    )
    private fun getCountryList() = listOf(
        Country("Nhật Bản", "manga"),
        Country("Trung Quốc", "manhua"),
        Country("Hàn Quốc", "manhwa"),
        Country("Khác", "other"),
    )
    private fun getSortByList() = arrayOf(
        SortBy("Không", ""),
        SortBy("Mới nhất", "latest"),
        SortBy("Cũ nhất", "oldest"),
        SortBy("Đánh giá", "rating"),
        SortBy("A-Z", "alphabet"),
        SortBy("Mới cập nhật", "recently_updated"),
        SortBy("Xem nhiều nhất", "mostView"),
    )
    private fun getChapterCountList() = arrayOf(
        ChapterCount("Không", ""),
        ChapterCount(">= 1 chapters", "1"),
        ChapterCount(">= 3 chapters", "3"),
        ChapterCount(">= 5 chapters", "5"),
        ChapterCount(">= 10 chapters", "10"),
        ChapterCount(">= 20 chapters", "20"),
        ChapterCount(">= 30 chapters", "30"),
        ChapterCount(">= 50 chapters", "50"),
    )
    private fun getStatusList() = listOf(
        Status("Hoàn thành", "1"),
        Status("Đang tiến hành", "0"),
    )
    private fun getGenreList() = listOf(
        Genre("Action", "1"),
        Genre("Adventure", "2"),
        Genre("Fantasy", "3"),
        Genre("Manhua", "4"),
        Genre("Chuyển", "5"),
        Genre("Truyện", "6"),
        Genre("Xuyên", "7"),
        Genre("Manhwa", "8"),
        Genre("Drama", "9"),
        Genre("Historical", "10"),
        Genre("Manga", "11"),
        Genre("Seinen", "12"),
        Genre("Comedy", "13"),
        Genre("Martial", "14"),
        Genre("Mystery", "15"),
        Genre("Romance", "16"),
        Genre("Shounen", "17"),
        Genre("Sports", "18"),
        Genre("Supernatural", "19"),
        Genre("Harem", "20"),
        Genre("Webtoon", "21"),
        Genre("School", "22"),
        Genre("Psychological", "23"),
        Genre("Cổ", "24"),
        Genre("Ecchi", "25"),
        Genre("Gender", "26"),
        Genre("Shoujo", "27"),
        Genre("Slice", "28"),
        Genre("Ngôn", "29"),
        Genre("Horror", "30"),
        Genre("Sci", "31"),
        Genre("Tragedy", "32"),
        Genre("Mecha", "33"),
        Genre("Comic", "34"),
        Genre("One", "35"),
        Genre("Shoujo", "36"),
        Genre("Anime", "37"),
        Genre("Josei", "38"),
        Genre("Smut", "39"),
        Genre("Shounen", "40"),
        Genre("Mature", "41"),
        Genre("Soft", "42"),
        Genre("Adult", "43"),
        Genre("Doujinshi", "44"),
        Genre("Live", "45"),
        Genre("Trinh", "46"),
        Genre("Việt", "47"),
        Genre("Truyện", "48"),
        Genre("Cooking", "49"),
        Genre("Tạp", "50"),
        Genre("16", "51"),
        Genre("Thiếu", "52"),
        Genre("Soft", "53"),
        Genre("Đam", "54"),
        Genre("BoyLove", "55"),
        Genre("Yaoi", "56"),
        Genre("18", "57"),
        Genre("Người", "58"),
        Genre("ABO", "59"),
        Genre("Mafia", "60"),
        Genre("Isekai", "61"),
        Genre("Hệ", "62"),
        Genre("NTR", "63"),
        Genre("Yuri", "64"),
        Genre("Girl", "65"),
        Genre("Demons", "66"),
        Genre("Huyền", "67"),
        Genre("Detective", "68"),
        Genre("Trọng", "69"),
        Genre("Magic", "70"),
    )

    private val preferences: SharedPreferences = getPreferences()

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
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
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
        }
        screen.addPreference(baseUrlPref)
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
