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
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.getPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Single
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ManhuaRock : ParsedHttpSource(), ConfigurableSource {

    // Site changed from FMReader to some Madara copycat
    override val versionId = 2

    override val name = "ManhuaRock"

    override val lang = "vi"

    private val defaultBaseUrl = "https://manhuarock1.site"

    override val baseUrl by lazy { getPrefBaseUrl() }

    private val preferences: SharedPreferences = getPreferences()

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    private val dateFormat by lazy {
        SimpleDateFormat("dd MMM yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/tat-ca-truyen/$page/?sort=most-viewd")

    override fun popularMangaSelector() = "div.listupd div.page-item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val a = element.selectFirst("a")!!

        setUrlWithoutDomain(a.attr("abs:href"))
        title = a.attr("title")
        thumbnail_url = element.selectFirst("img")?.attr("abs:data-src")
    }

    override fun popularMangaNextPageSelector() = "li.next:not(.disabled)"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/tat-ca-truyen/$page/?sort=latest-updated")

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addPathSegment(page.toString())
                addQueryParameter("keyword", query)
            } else {
                (if (filters.isEmpty()) getFilterList() else filters).forEach {
                    when (it) {
                        is OrderByFilter -> addQueryParameter("sort", it.values[it.state].slug)
                        is GenreList -> addPathSegments(it.values[it.state].slug)
                        else -> {}
                    }
                }
                addPathSegment(page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("div.post-title h1")!!.text()
        author = document.selectFirst("div.author-content")?.text()
        artist = document.selectFirst("div.artist-content")?.text()
        description = document.selectFirst("div.dsct")?.text()
        genre = document.select("div.genres-content a[rel=tag]").joinToString { it.text() }
        status = when (document.selectFirst("div.summary-heading:contains(Tình Trạng) + div.summary-content")?.text()) {
            // I have zero idea what the strings for Ongoing and Completed are, these are educated guesses
            // All the metadata on this page is basically "Unknown".
            "Đang Ra" -> SManga.ONGOING
            "Hoàn Thành" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("div.summary_image img")?.attr("abs:data-src")
    }

    override fun chapterListSelector() = "ul.row-content-chapter li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val a = element.selectFirst("a")!!

        setUrlWithoutDomain(a.attr("abs:href"))
        name = a.text()
        date_upload = runCatching {
            val date = element.selectFirst("span.chapter-time")!!.text()

            dateFormat.parse(date)!!.time
        }.getOrDefault(0L)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.split('/').last()

        return GET("$baseUrl/ajax/image/list/chap/$chapterId?mode=vertical&quality=high")
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterId = response.request.url.pathSegments.last()

        countViews(chapterId)

        val data = json.decodeFromString<AjaxImageListResponse>(response.body.string())

        if (!data.status || data.html == null) {
            throw Exception(data.msg ?: "Lỗi không xác định khi lấy trang")
        }

        return pageListParse(Jsoup.parse(data.html, baseUrl))
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img").mapIndexed { i, it ->
            Page(i, imageUrl = it.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

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

    private class OrderByFilter : Filter.Select<Slug>(
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
        Slug("Xuyên Không", "the-loai/xuyen-khong"),
        Slug("Webtoon", "the-loai/webtoon"),
        Slug("Truyện Màu", "the-loai/truyen-mau"),
        Slug("Trọng Sinh", "the-loai/trong-sinh"),
        Slug("Tragedy", "the-loai/tragedy"),
        Slug("Supernatural", "the-loai/supernatural"),
        Slug("Sports", "the-loai/sports"),
        Slug("Slice Of Life", "the-loai/slice-of-life"),
        Slug("Shounen", "the-loai/shounen"),
        Slug("Shoujo", "the-loai/shoujo"),
        Slug("Sci-Fi", "the-loai/sci-fi"),
        Slug("School Life", "the-loai/school-life"),
        Slug("Romance", "the-loai/romance"),
        Slug("Psychological", "the-loai/psychological"),
        Slug("Ngôn Tình", "the-loai/ngon-tinh"),
        Slug("Mystery", "the-loai/mystery"),
        Slug("Mature", "the-loai/mature"),
        Slug("Martial Arts", "the-loai/martial-arts"),
        Slug("Manhwa", "the-loai/manhwa"),
        Slug("Manhua", "the-loai/manhua"),
        Slug("Josei", "the-loai/josei"),
        Slug("Isekai", "the-loai/isekai"),
        Slug("Huyền Huyễn", "the-loai/huyen-huyen"),
        Slug("Horror", "the-loai/horror"),
        Slug("Historical", "the-loai/historical"),
        Slug("Harem", "the-loai/harem"),
        Slug("Gender Bender", "the-loai/gender-bender"),
        Slug("Fantasy", "the-loai/fantasy"),
        Slug("Ecchi", "the-loai/ecchi"),
        Slug("Drama", "the-loai/drama"),
        Slug("Detective", "the-loai/detective"),
        Slug("Demons", "the-loai/demons"),
        Slug("Comedy", "the-loai/comedy"),
        Slug("Cổ Đại", "the-loai/co-dai"),
        Slug("Chuyển Sinh", "the-loai/chuyen-sinh"),
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
        }.let(screen::addPreference)
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
