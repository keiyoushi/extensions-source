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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/danh-sach/truyen-moi-cap-nhat?page=$page", headers)

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst("a.line-clamp-2").let {
            setUrlWithoutDomain(it!!.absUrl("href"))
            title = it.text()
        }
        thumbnail_url = element.selectFirst("img")
            ?.absUrl("src")
            ?.let { url ->
                url.toHttpUrlOrNull()
                    ?.queryParameter("url")
                    ?: url
            }
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
        when (date.replace(Regex("[0-9]"), "").lowercase().trim()) {
            "giây trước" -> calendar.apply { add(Calendar.SECOND, -number) }.timeInMillis
            "phút trước" -> calendar.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            "giờ trước" -> calendar.apply { add(Calendar.HOUR, -number) }.timeInMillis
            "ngày trước" -> calendar.apply { add(Calendar.DAY_OF_YEAR, -number) }.timeInMillis
            else -> dateFormat.tryParse(date)
        }
    }.getOrNull() ?: 0L

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("section aside:first-child h1").text()
        genre = document.select("span:contains(Thể loại:) ~ a").joinToString { it.text().trim(',', ' ') }
        description = document.select("div.mt-3").joinToString {
            it.select("a, strong").unwrap()
            it.wholeText().trim()
        }
        thumbnail_url = document.selectFirst("section aside:first-child img")
            ?.absUrl("src")
            ?.let { url ->
                url.toHttpUrlOrNull()
                    ?.queryParameter("url")
                    ?: url
            }
        status = parseStatus(document.selectFirst("span:contains(Trạng thái:) + b")?.text())
        author = document.selectFirst("span:contains(Tác giả:) + b")?.text()
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        listOf("Đang Tiến Hành", "Đang Cập Nhật").any { status.contains(it, ignoreCase = true) } -> SManga.ONGOING
        listOf("Hoàn Thành", "Đã Hoàn Thành").any { status.contains(it, ignoreCase = true) } -> SManga.COMPLETED
        listOf("Tạm Ngưng", "Tạm Hoãn").any { status.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
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
        val json = response.parseAs<SearchDTO>()
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
        Genre("Chuyển Sinh", "5"),
        Genre("Truyện Màu", "6"),
        Genre("Xuyên Không", "7"),
        Genre("Manhwa", "8"),
        Genre("Drama", "9"),
        Genre("Historical", "10"),
        Genre("Manga", "11"),
        Genre("Seinen", "12"),
        Genre("Comedy", "13"),
        Genre("Martial Arts", "14"),
        Genre("Mystery", "15"),
        Genre("Romance", "16"),
        Genre("Shounen", "17"),
        Genre("Sports", "18"),
        Genre("Supernatural", "19"),
        Genre("Harem", "20"),
        Genre("Webtoon", "21"),
        Genre("School", "22"),
        Genre("Psychological", "23"),
        Genre("Cổ Đại", "24"),
        Genre("Ecchi", "25"),
        Genre("Gender Bender", "26"),
        Genre("Shoujo", "27"),
        Genre("Slice of Life", "28"),
        Genre("Ngôn Tình", "29"),
        Genre("Horror", "30"),
        Genre("Sci-fi", "31"),
        Genre("Tragedy", "32"),
        Genre("Mecha", "33"),
        Genre("Comic", "34"),
        Genre("One shot", "35"),
        Genre("Shoujo Ai", "36"),
        Genre("Anime", "37"),
        Genre("Josei", "38"),
        Genre("Smut", "39"),
        Genre("Shounen Ai", "40"),
        Genre("Mature", "41"),
        Genre("Soft Yuri", "42"),
        Genre("Adult", "43"),
        Genre("Doujinshi", "44"),
        Genre("Live action", "45"),
        Genre("Trinh Thám", "46"),
        Genre("Việt Nam", "47"),
        Genre("Truyện Scan", "48"),
        Genre("Cooking", "49"),
        Genre("Tạp chí truyện tranh", "50"),
        Genre("16+", "51"),
        Genre("Thiếu Nhi", "52"),
        Genre("Soft Yaoi", "53"),
        Genre("Đam Mỹ", "54"),
        Genre("BoyLove", "55"),
        Genre("Yaoi", "56"),
        Genre("18+", "57"),
        Genre("Người Thú", "58"),
        Genre("ABO", "59"),
        Genre("Mafia", "60"),
        Genre("Isekai", "61"),
        Genre("Hệ Thống", "62"),
        Genre("NTR", "63"),
        Genre("Yuri", "64"),
        Genre("Girl Love", "65"),
        Genre("Demons", "66"),
        Genre("Huyền Huyễn", "67"),
        Genre("Detective", "68"),
        Genre("Trọng Sinh", "69"),
        Genre("Magic", "70"),
        Genre("Military", "71"),
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
