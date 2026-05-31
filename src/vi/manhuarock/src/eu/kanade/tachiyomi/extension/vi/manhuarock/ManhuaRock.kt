package eu.kanade.tachiyomi.extension.vi.manhuarock

import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import rx.Single
import rx.schedulers.Schedulers
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ManhuaRock :
    HttpSource(),
    ConfigurableSource {

    // Site changed from FMReader to some Madara copycat
    override val versionId = 2

    override val name = "ManhuaRock"

    override val lang = "vi"

    private val defaultBaseUrl = "https://manhuarock4.site"

    override val baseUrl get() = getPrefBaseUrl()

    private val preferences: SharedPreferences = getPreferences()

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }

    // ============================== Common ======================================

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".latest-manga-grid .latest-manga-card").map { element: Element ->
            SManga.create().apply {
                element.selectFirst(".latest-manga-title a")!!.also { it: Element ->
                    title = it.text()
                    setUrlWithoutDomain(it.attr("abs:href"))
                }
                thumbnail_url = element.selectFirst(".latest-manga-thumb-wrapper img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("li.next:not(.disabled)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Popular ======================================

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/tat-ca-truyen/$page/?sort=most-viewd", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Latest ======================================

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/tat-ca-truyen/$page/?sort=latest-updated", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Search ======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addPathSegment(page.toString())
                addQueryParameter("keyword", query)
            } else {
                (filters.ifEmpty { getFilterList() }).forEach { filter ->
                    when (filter) {
                        is OrderByFilter -> addQueryParameter("sort", filter.values[filter.state].slug)
                        is GenreList -> addPathSegments(filter.values[filter.state].slug)
                        else -> {}
                    }
                }
                addPathSegment(page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Details ======================================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("div.post-title h1")!!.text()
        author = document.selectFirst("div.author-content")?.text()
        artist = document.selectFirst("div.artist-content")?.text()
        description = document.selectFirst("div.dsct")?.wholeText()?.trim()
        genre = document.select("div.genres-content a[rel=tag]").joinToString { it.text() }
        status = when (document.selectFirst("div.summary-heading:contains(Tình Trạng) + div.summary-content")?.text()) {
            "Đang Tiến Hành" -> SManga.ONGOING
            "Hoàn Thành" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("div.summary_image img")?.attr("abs:data-src")
    }

    // ============================== Chapters ======================================

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("ul.row-content-chapter li").map { element: Element ->
            SChapter.create().apply {
                element.selectFirst("a")!!.also { it: Element ->
                    setUrlWithoutDomain(it.attr("abs:href"))
                    name = it.text()
                }
                date_upload = parseRelativeDate(element.select("span.chapter-time").text())
            }
        }

    private fun parseRelativeDate(date: String): Long {
        val value = Regex("""\d+""").find(date)?.value?.toIntOrNull() ?: return 0L
        val calendar = Calendar.getInstance()
        when {
            date.contains("giây") -> calendar.add(Calendar.SECOND, -value)
            date.contains("phút") -> calendar.add(Calendar.MINUTE, -value)
            date.contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -value)
            date.contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -value)
            date.contains("tuần") -> calendar.add(Calendar.WEEK_OF_MONTH, -value)
            date.contains("tháng") -> calendar.add(Calendar.MONTH, -value)
            date.contains("năm") -> calendar.add(Calendar.YEAR, -value)
            else -> return dateFormat.tryParse(date)
        }
        return calendar.timeInMillis
    }

    override fun getChapterUrl(chapter: SChapter): String {
        if (!chapter.url.endsWith(".html")) {
            return baseUrl + chapter.url.replace("truyen-tranh", "truyen").removeSuffix("/")
                .substringBeforeLast("/")
        }
        return baseUrl + chapter.url
    }

    private fun getChapterId(url: String): String {
        val lastSegment = url.toHttpUrl().pathSegments.last()
        return if (lastSegment.all { it.isDigit() }) {
            lastSegment
        } else {
            val response = client.newCall(GET(url, headers)).execute()
            val doc = response.asJsoup()
            val scriptContent = doc.selectFirst("script:containsData(chapter_id)")!!.data()
            scriptContent.substringAfter("chapter_id = ")
                .substringBefore(",")
                .trim()
        }
    }

    // ============================== Pages ======================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = getChapterId(baseUrl + chapter.url)
        return GET("$baseUrl/ajax/image/list/chap/$chapterId?mode=vertical&quality=high", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterId = response.request.url.pathSegments.last()
        countViews(chapterId)

        val data = response.parseAs<AjaxImageListResponse>()
        if (!data.status || data.html == null) {
            throw Exception(data.msg ?: "Lỗi không xác định khi lấy trang")
        }

        val document = Jsoup.parse(data.html, baseUrl)
        return document.select("img").mapIndexed { i, it: Element ->
            Page(i, imageUrl = it.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ======================================

    override fun getFilterList() = FilterList(
        Filter.Header("Không dùng chung với tìm kiếm bằng từ khoá."),
        OrderByFilter(),
        GenreList(getGenreList()),
    )

    private fun countViews(chapterId: String) {
        val req = POST("$baseUrl/ajax/manga/count-view/$chapterId")

        Single.fromCallable {
            client.newCall(req).execute().close()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe(
                {},
                {
                    Log.e("manhuarock", "Could not count chapter view: ${it.stackTraceToString()}")
                },
            )
    }

    @Serializable
    private data class AjaxImageListResponse(
        val status: Boolean = false,
        val msg: String? = null,
        val html: String? = null,
    )

    private class Slug(val name: String, val slug: String) {
        override fun toString() = name
    }

    private class OrderByFilter :
        Filter.Select<Slug>(
            "Sắp xếp theo",
            arrayOf(
                Slug("Mới cập nhật", "latest-updated"),
                Slug("Điểm", "score"),
                Slug("Tên A-Z", "name-az"),
                Slug("Ngày phát hành", "release-date"),
                Slug("Xem nhiều", "most-viewd"),
            ),
            4,
        )

    private class GenreList(slugs: Array<Slug>) : Filter.Select<Slug>("Thể loại", slugs)

    private fun getGenreList() = arrayOf(
        Slug("Tất cả", "tat-ca-truyen"),
        Slug("Hoàn thành", "hoan-thanh"),
        Slug("Yuri", "the-loai/yuri"),
        Slug("Xuyên Không", "the-loai/xuyen-khong"),
        Slug("Webtoon", "the-loai/webtoons"),
        Slug("Webtoon", "the-loai/webtoon"),
        Slug("Võ Thuật", "the-loai/vo-thuat"),
        Slug("Võ Lâm", "the-loai/vo-lam"),
        Slug("Viễn Tưởng", "the-loai/vien-tuong"),
        Slug("Tu Tiên", "the-loai/tu-tien"),
        Slug("Truyện Trung", "the-loai/truyen-trung"),
        Slug("Truyện Màu", "the-loai/truyen-mau"),
        Slug("Trùng Sinh", "the-loai/trung-sinh"),
        Slug("Trọng Sinh", "the-loai/trong-sinh"),
        Slug("Tragedy", "the-loai/tragedy"),
        Slug("Supernatural", "the-loai/supernatural"),
        Slug("Sports", "the-loai/sports"),
        Slug("Slice Of Life", "the-loai/slice-of-life"),
        Slug("Siêu Nhiên", "the-loai/sieu-nhien"),
        Slug("Shounen Ai", "the-loai/shounen-ai"),
        Slug("Shounen", "the-loai/shounen"),
        Slug("Shoujo", "the-loai/shoujo"),
        Slug("Seinen", "the-loai/seinen"),
        Slug("Sci-Fi", "the-loai/sci-fi"),
        Slug("School Life", "the-loai/school-life"),
        Slug("Romance", "the-loai/romance"),
        Slug("Psychological", "the-loai/psychological"),
        Slug("Phiêu Lưu", "the-loai/phieu-luu"),
        Slug("One Shot", "the-loai/one-shot"),
        Slug("Ngôn Tình", "the-loai/ngon-tinh"),
        Slug("Mystery", "the-loai/mystery"),
        Slug("Murim", "the-loai/murim"),
        Slug("Mecha", "the-loai/mecha"),
        Slug("Mature", "the-loai/mature"),
        Slug("Mạt thế", "the-loai/mat-the"),
        Slug("Martial Arts", "the-loai/martial-arts"),
        Slug("Manhwa", "the-loai/manhwa"),
        Slug("Manhua", "the-loai/manhua"),
        Slug("Manga", "the-loai/manga"),
        Slug("Magic", "the-loai/magic"),
        Slug("Lịch Sử", "the-loai/lich-su"),
        Slug("Leo Tháp", "the-loai/leo-thap"),
        Slug("Lãng Mạn", "the-loai/lang-man"),
        Slug("Kinh Dị", "the-loai/kinh-di"),
        Slug("Khoa Học", "the-loai/khoa-hoc"),
        Slug("Josei", "the-loai/josei"),
        Slug("Isekai", "the-loai/isekai"),
        Slug("Huyền Huyễn", "the-loai/huyen-huyen"),
        Slug("Huyền Bí", "the-loai/huyen-bi"),
        Slug("Horror", "the-loai/horror"),
        Slug("Học Đường", "the-loai/hoc-duong"),
        Slug("Historical", "the-loai/historical"),
        Slug("Hiện Đại", "the-loai/hien-dai"),
        Slug("Hệ Thống", "the-loai/he-thong"),
        Slug("Harem", "the-loai/harem"),
        Slug("Hành Động", "the-loai/hanh-dong"),
        Slug("Hầm Ngục", "the-loai/ham-nguc"),
        Slug("Hài Hước", "the-loai/hai-huoc"),
        Slug("Gyaru", "the-loai/gyaru"),
        Slug("Gender Bender", "the-loai/gender-bender"),
        Slug("Game", "the-loai/game"),
        Slug("Gal", "the-loai/gal"),
        Slug("Fantasy", "the-loai/fantasy"),
        Slug("Ecchi", "the-loai/ecchi"),
        Slug("Drama", "the-loai/drama"),
        Slug("Doujinshi", "the-loai/doujinshi"),
        Slug("Detective", "the-loai/detective"),
        Slug("Demons", "the-loai/demons"),
        Slug("Comedy", "the-loai/comedy"),
        Slug("Cổ Đại", "the-loai/co-dai"),
        Slug("Chuyển Sinh", "the-loai/chuyen-sinh"),
        Slug("Bạo Lực", "the-loai/bao-luc"),
        Slug("Anime", "the-loai/anime"),
        Slug("Adventure", "the-loai/adventure"),
        Slug("Adult", "the-loai/adult"),
        Slug("Action", "the-loai/action"),
    )

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

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

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
        }.also(screen::addPreference)
    }

    companion object {
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng thay đổi."
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
    }
}
