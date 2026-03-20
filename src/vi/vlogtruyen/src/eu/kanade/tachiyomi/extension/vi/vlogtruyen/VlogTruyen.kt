package eu.kanade.tachiyomi.extension.vi.vlogtruyen

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class VlogTruyen :
    ParsedHttpSource(),
    ConfigurableSource {

    override val lang = "vi"

    override val name = "VlogTruyen"

    override val supportsLatest = true

    override val id: Long = 6425642624422299254

    private val defaultBaseUrl = "https://vlogtruyen65.com"

    override val baseUrl by lazy { getPrefBaseUrl() }

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val xhrHeaders by lazy {
        headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/the-loai/moi-cap-nhap/?page=$page", headers)

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.select("h3.title-commic-tab").text()
        thumbnail_url = element.selectFirst(".image-commic-tab img.lazyload")?.absUrl("data-src")
    }

    override fun latestUpdatesNextPageSelector() = ".pagination > li.active + li"

    override fun latestUpdatesSelector() = "div.content-tab ul li.commic-hover"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/the-loai/dang-hot?page=$page", headers)

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("h1.title-commic-detail").text()
        genre = document.select(".categories-list-detail-commic > li > a").joinToString { it.text().trim(',', ' ') }
        description = document.select("span.desc-commic-detail")
            .joinToString { it.wholeText().trim() }
        thumbnail_url = document.selectFirst("div.image-commic-detail > a > img")?.absUrl("data-src")
        status = parseStatus(document.selectFirst("div.top-detail-manga > div.top-detail-manga-avatar > div.manga-status > p")?.text())
        author = document.select(".h5-drawer:contains(Tác Giả) + ul li a").joinToString { it.text() }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        status.contains("Đã hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        status.contains("Tạm ngưng", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/").substringBeforeLast(".")
        return GET("$baseUrl/thong-tin-ca-nhan?manga_slug=$slug", xhrHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = response.parseAs<ChapterDTO>()
        val document = Jsoup.parseBodyFragment(json.data.chaptersHtml, response.request.url.toString())
        val hidePaidChapters = preferences.getBoolean(KEY_HIDE_PAID_CHAPTERS, false)
        return document.select(chapterListSelector()).filterNot {
            hidePaidChapters && it.select("li > b").isNotEmpty()
        }.map { element -> chapterFromElement(element) }
    }

    override fun chapterListSelector() = "li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        name = element.select("h3").text()
        if (element.select("li > b").text().isNotBlank()) {
            name += " " + element.select("li > b").text() +
                when (element.select("li > b > i").attr("class")) {
                    "fa fa-lock" -> " 🔒"
                    "fa fa-unlock" -> " 🔓"
                    else -> {}
                }
        }
        date_upload = dateFormat.tryParse(element.select("li:not(:has(> span.chapter-view)) > span, li > span:last-child").text())
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("tim-kiem")
                addQueryParameter("q", query)
                addQueryParameter("page", page.toString())
            } else {
                (if (filters.isEmpty()) getFilterList() else filters).forEach {
                    when (it) {
                        is GenreList -> addPathSegments(it.values[it.state].genre)
                        is StatusByFilter -> addQueryParameter("status", it.values[it.state].genre)
                        is OrderByFilter -> addQueryParameter("sort", it.values[it.state].genre)
                        else -> {}
                    }
                }
                addQueryParameter("page", page.toString())
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector(): String = latestUpdatesNextPageSelector()

    override fun searchMangaSelector(): String = latestUpdatesSelector()

    override fun pageListParse(document: Document): List<Page> {
        val loginRequired = document.selectFirst(".area-show-content span")

        if (loginRequired?.text() == "Xin lỗi, bạn cần đăng nhập để đọc được chapter này!") {
            throw Exception("${loginRequired.text()} \n Hãy đăng nhập trong WebView.")
        }
        return document.select("img.image-commic").mapIndexed { i, e ->
            Page(i, imageUrl = e.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document): String = ""

    override fun getFilterList() = FilterList(
        Filter.Header("Không dùng chung với tìm kiếm bằng từ khoá."),
        StatusByFilter(),
        OrderByFilter(),
        GenreList(getGenreList()),
    )

    private class OrderByFilter :
        Filter.Select<Genre>(
            "Sắp xếp theo",
            arrayOf(
                Genre("Mới nhất", "moi-nhat"),
                Genre("Đang hot", "dang-hot"),
                Genre("Cũ nhất", "cu-nhat"),
            ),
        )

    private class StatusByFilter :
        Filter.Select<Genre>(
            "Trạng thái",
            arrayOf(
                Genre("Trạng thái", "Trang-thai"),
                Genre("Đã hoàn thành", "1"),
                Genre("Chưa hoàn thành", "2"),
            ),
        )

    private class GenreList(genre: Array<Genre>) : Filter.Select<Genre>("Thể loại", genre)

    private fun getGenreList() = arrayOf(
        Genre("Hành Động", "the-loai/hanh-dong"),
        Genre("Fantasy", "the-loai/fantasy"),
        Genre("Truyện Trung", "the-loai/manhua"),
        Genre("Võ Thuật", "the-loai/vo-thuat"),
        Genre("Truyện Màu", "the-loai/truyen-mau"),
        Genre("Chuyển Sinh", "the-loai/chuyen-sinh"),
        Genre("Bí Ẩn", "the-loai/mystery"),
        Genre("Ngôn Tình", "the-loai/ngon-tinh"),
        Genre("Manhwa", "the-loai/manhwa"),
        Genre("Phiêu Lưu", "the-loai/adventure"),
        Genre("Cổ Đại", "the-loai/co"),
        Genre("Hài Hước", "the-loai/hai"),
        Genre("Kịch Tính", "the-loai/drama"),
        Genre("Lịch Sử", "the-loai/historical"),
        Genre("Xuyên Không", "the-loai/xuyen-khong"),
        Genre("Lãng Mạn", "the-loai/romance"),
        Genre("Học Đường", "the-loai/school-life"),
        Genre("Đời Thường", "the-loai/slice-of-life"),
        Genre("Siêu Nhiên", "the-loai/supernatural"),
        Genre("Truyện Âu Mỹ", "the-loai/comic"),
        Genre("Việt Nam", "the-loai/viet-nam"),
        Genre("Shounen", "the-loai/shounen"),
        Genre("Webtoon", "the-loai/webtoon"),
        Genre("Kinh Dị", "the-loai/horror"),
        Genre("Tâm Lý", "the-loai/psychological"),
        Genre("Seinen", "the-loai/seinen"),
        Genre("Manga", "the-loai/manga"),
        Genre("Khoa Học Viễn Tưởng", "the-loai/sci-fi"),
        Genre("Bi Kịch", "the-loai/tragedy"),
        Genre("Thể Thao", "the-loai/sports"),
        Genre("Anime", "the-loai/anime"),
        Genre("Thiếu Nhi", "the-loai/thieu-nhi"),
        Genre("Người Máy", "the-loai/mecha"),
        Genre("Trinh Thám", "the-loai/trinh-tham"),
        Genre("One shot", "the-loai/one-shot"),
        Genre("Tạp chí truyện tranh", "the-loai/tap-chi-truyen-tranh"),
        Genre("Doujinshi", "the-loai/doujinshi"),
        Genre("Live action", "the-loai/live-action"),
        Genre("Nấu Nướng", "the-loai/cooking"),
        Genre("Truyện scan", "the-loai/truyen-scan"),
        Genre("Cổ Đại", "the-loai/co-dai"),
        Genre("Detective", "the-loai/detective"),
        Genre("Trọng Sinh", "the-loai/trong-sinh"),
        Genre("Chuyển sinh", "the-loai/isekai"),
        Genre("Huyền Huyễn", "the-loai/huyen-huyen"),
        Genre("Game", "the-loai/game"),
        Genre("Chuyển sinh", "the-loai/isekaidi-gioitrong-sinh"),
        Genre("Tu tiên", "the-loai/tu-tien"),
        Genre("Hệ Thống", "the-loai/he-thong"),
        Genre("Võ lâm", "the-loai/vo-lam"),
        Genre("Già Gân", "the-loai/gia-gan"),
        Genre("Hồi Quy", "the-loai/hoi-quy"),
        Genre("Bắt Nạt", "the-loai/bat-nat"),
        Genre("Báo Thù", "the-loai/bao-thu"),
        Genre("Đấu Trí", "the-loai/dau-tri"),
        Genre("Tài Chính", "the-loai/tai-chinh"),
        Genre("Tận Thế", "the-loai/tan-the"),
        Genre("Sinh Tồn", "the-loai/sinh-ton"),
        Genre("Phản Diện", "the-loai/phan-dien"),
        Genre("Martial Arts", "the-loai/martial-arts"),
        Genre("Hành Động", "the-loai/action"),
        Genre("Comedy", "the-loai/comedy"),
        Genre("Âm Nhạc", "the-loai/am-nhac"),
        Genre("Công Sở", "the-loai/cong-so"),
        Genre("Diễn Viên", "the-loai/dien-vien"),
        Genre("Vlogtruyen", "the-loai/vlogtruyen"),
    )
    private class Genre(val name: String, val genre: String) {
        override fun toString() = name
    }
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

        SwitchPreferenceCompat(screen.context).apply {
            key = KEY_HIDE_PAID_CHAPTERS
            title = "Ẩn các chương cần tài khoản"
            summary = "Ẩn các chương truyện cần nạp xu để đọc."
            setDefaultValue(false)
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
        private const val KEY_HIDE_PAID_CHAPTERS = "hidePaidChapters"
    }
}
