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
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class VlogTruyen :
    HttpSource(),
    ConfigurableSource {

    override val lang = "vi"

    override val name = "VlogTruyen"

    override val supportsLatest = true

    override val id: Long = 6425642624422299254

    private val defaultBaseUrl = "https://vlogtruyen69.com"

    override val baseUrl get() = getPrefBaseUrl()

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

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

    // ============================== Popular ======================================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/the-loai/dang-hot?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Latest ======================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/the-loai/moi-cap-nhap/?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Search ======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("tim-kiem")
                addQueryParameter("q", query)
                addQueryParameter("page", page.toString())
            } else {
                addPathSegment("the-loai")
                (filters.ifEmpty { getFilterList() }).forEach {
                    when (it) {
                        is GenreList -> addPathSegment(it.values[it.state].genre)
                        is StatusByFilter -> addQueryParameter("status", it.values[it.state].genre)
                        is SorByFilter -> addQueryParameter("sort", it.values[it.state].genre)
                        else -> {}
                    }
                }
                addQueryParameter("page", page.toString())
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.content-tab ul li.commic-hover").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                title = element.select("h3.title-commic-tab").text()
                thumbnail_url = element.selectFirst(".image-commic-tab img.lazyload")?.absUrl("data-src")
            }
        }
        val hasNextPage = document.selectFirst(".pagination > li.active + li") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ======================================

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.select("h1.title-commic-detail").text()
        genre = document.select(".categories-list-detail-commic > li > a").joinToString { it.text().trim(',', ' ') }
        description = document.select("span.desc-commic-detail").joinToString { it.wholeText().trim() }
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

    // ============================== Chapters ======================================

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/").substringBeforeLast(".")
        return GET("$baseUrl/thong-tin-ca-nhan?manga_slug=$slug", xhrHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = response.parseAs<ChapterDTO>()
        val document = Jsoup.parseBodyFragment(json.data.chaptersHtml, response.request.url.toString())
        val hidePaidChapters = preferences.getBoolean(KEY_HIDE_PAID_CHAPTERS, false)
        return document.select("li").filterNot {
            hidePaidChapters && it.select("li > b").isNotEmpty()
        }.map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                name = element.select("h3").text()
                if (element.select("li > b").text().isNotBlank()) {
                    name += " " + element.select("li > b").text() +
                        when (element.select("li > b > i").attr("class")) {
                            "fa fa-lock" -> " 🔒"
                            "fa fa-unlock" -> " 🔓"
                            else -> ""
                        }
                }
                date_upload = dateFormat.tryParse(element.select("li:not(:has(> span.chapter-view)) > span, li > span:last-child").text())
            }
        }
    }

    // ============================== Pages ======================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val loginRequired = document.selectFirst(".area-show-content span")

        if (loginRequired?.text() == "Xin lỗi, bạn cần đăng nhập để đọc được chapter này!") {
            throw Exception("${loginRequired.text()} \n Hãy đăng nhập trong WebView.")
        }
        return document.select("img.image-commic").mapIndexed { i, e ->
            Page(i, imageUrl = e.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ======================================

    override fun getFilterList() = FilterList(
        Filter.Header("Không dùng chung với tìm kiếm bằng từ khoá."),
        StatusByFilter(),
        SorByFilter(),
        GenreList(getGenreList()),
    )

    private class SorByFilter :
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
                Genre("Trạng thái", "Trạng thái"),
                Genre("Đã hoàn thành", "1"),
                Genre("Chưa hoàn thành", "2"),
            ),
        )

    private class GenreList(genre: Array<Genre>) : Filter.Select<Genre>("Thể loại", genre)

    private fun getGenreList() = arrayOf(
        Genre("Hành Động", "hanh-dong"),
        Genre("Fantasy", "fantasy"),
        Genre("Truyện Trung", "manhua"),
        Genre("Võ Thuật", "vo-thuat"),
        Genre("Truyện Màu", "truyen-mau"),
        Genre("Chuyển Sinh", "chuyen-sinh"),
        Genre("Bí Ẩn", "mystery"),
        Genre("Ngôn Tình", "ngon-tinh"),
        Genre("Manhwa", "manhwa"),
        Genre("Phiêu Lưu", "adventure"),
        Genre("Cổ Đại", "co-ai"),
        Genre("Hài Hước", "hai-huoc"),
        Genre("Kịch Tính", "drama"),
        Genre("Lịch Sử", "historical"),
        Genre("Xuyên Không", "xuyen-khong"),
        Genre("Lãng Mạn", "romance"),
        Genre("Học Đường", "school-life"),
        Genre("Đời Thường", "slice-of-life"),
        Genre("Siêu Nhiên", "supernatural"),
        Genre("Truyện Âu Mỹ", "comic"),
        Genre("Việt Nam", "viet-nam"),
        Genre("Shounen", "shounen"),
        Genre("Webtoon", "webtoon"),
        Genre("Kinh Dị", "horror"),
        Genre("Tâm Lý", "psychological"),
        Genre("Seinen", "seinen"),
        Genre("Manga", "manga"),
        Genre("Khoa Học Viễn Tưởng", "sci-fi"),
        Genre("Bi Kịch", "tragedy"),
        Genre("Thể Thao", "sports"),
        Genre("Anime", "anime"),
        Genre("Thiếu Nhi", "thieu-nhi"),
        Genre("Người Máy", "mecha"),
        Genre("Trinh Thám", "trinh-tham"),
        Genre("One shot", "one-shot"),
        Genre("Tạp chí truyện tranh", "tap-chi-truyen-tranh"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Live action", "live-action"),
        Genre("Nấu Nướng", "cooking"),
        Genre("Truyện scan", "truyen-scan"),
        Genre("Cổ Đại", "co-dai"),
        Genre("Detective", "detective"),
        Genre("Trọng Sinh", "trong-sinh"),
        Genre("Chuyển sinh", "isekai"),
        Genre("Huyền Huyễn", "huyen-huyen"),
        Genre("Game", "game"),
        Genre("Chuyển sinh", "isekaidi-gioitrong-sinh"),
        Genre("Tu tiên", "tu-tien"),
        Genre("Hệ Thống", "he-thong"),
        Genre("Võ lâm", "vo-lam"),
        Genre("Già Gân", "gia-gan"),
        Genre("Hồi Quy", "hoi-quy"),
        Genre("Bắt Nạt", "bat-nat"),
        Genre("Báo Thù", "bao-thu"),
        Genre("Đấu Trí", "dau-tri"),
        Genre("Tài Chính", "tai-chinh"),
        Genre("Tận Thế", "tan-the"),
        Genre("Sinh Tồn", "sinh-ton"),
        Genre("Phản Diện", "phan-dien"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Hành Động", "action"),
        Genre("Comedy", "comedy"),
        Genre("Âm Nhạc", "am-nhac"),
        Genre("Công Sở", "cong-so"),
        Genre("Diễn Viên", "dien-vien"),
        Genre("Vlogtruyen", "vlogtruyen"),
        Genre("Ngon Tinh", "ng244n-t236nh"),
        Genre("Linh Dị", "linh-di"),
        Genre("Y học", "y-hoc"),
        Genre("Xã Hội Đen", "xa-hoi-den"),
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
