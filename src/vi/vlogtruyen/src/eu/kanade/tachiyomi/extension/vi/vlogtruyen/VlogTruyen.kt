package eu.kanade.tachiyomi.extension.vi.vlogtruyen

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class VlogTruyen : ParsedHttpSource(), ConfigurableSource {

    override val lang = "vi"

    override val name = "VlogTruyen"

    override val supportsLatest = true

    private val defaultBaseUrl = "https://vlogtruyen33.com"

    override val baseUrl by lazy { getPrefBaseUrl() }

    private val searchURL by lazy { "$baseUrl/tim-kiem" }

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("X-Requested-With", "XMLHttpRequest")

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/the-loai/moi-cap-nhap/?page=$page", headers)

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.select("h3.title-commic-tab").text()
        thumbnail_url = element.selectFirst(".image-commic-tab img.lazyload")?.attr("data-src")
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
        description = document.select("div.top-detail-manga > div.top-detail-manga-content > span.desc-commic-detail").text()
        thumbnail_url = document.select("div.image-commic-detail > a > img").attr("data-src")
        status = parseStatus(document.selectFirst("div.top-detail-manga > div.top-detail-manga-avatar > div.manga-status > p")?.text())
        author = document.select(".h5-drawer:contains(Tác Giả) + ul li a").joinToString { it.text() }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Đang tiến hành") -> SManga.ONGOING
        status.contains("Đã hoàn thành") -> SManga.COMPLETED
        status.contains("Tạm ngưng") -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = json.decodeFromString<ChapterDTO>(response.body.string().replace("\\n", ""))
        val document = Jsoup.parseBodyFragment(json.data.chaptersHtml, response.request.url.toString())
        val hidePaidChapters = preferences.getBoolean(KEY_HIDE_PAID_CHAPTERS, false)
        return document.select("li, .ul-list-chaper-detail-commic li").filterNot {
            if (hidePaidChapters) {
                it.select("li:not(:has(> b))").text().isBlank().or(!hidePaidChapters)
            } else {
                it.select("li > a").text().isBlank().or(false)
            }
        }
            .mapNotNull {
                SChapter.create().apply {
                    setUrlWithoutDomain(it.selectFirst("a")!!.attr("href"))
                    name = it.select("h3").first()!!.text().trim()
                    if (it.select("li > b").text().isNotBlank()) {
                        name += " " + it.select("li > b").text() + " 🔒"
                    }
                    date_upload = parseDate(it.select("li:not(:has(> span.chapter-view)) > span, li > span:last-child").text())
                }
            }
    }

    private fun parseDate(date: String): Long = runCatching {
        dateFormat.parse(date)?.time
    }.getOrNull() ?: 0L

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val url = client.newCall(GET(baseUrl + manga.url, headers)).execute().asJsoup()
        if (checkChapterLists(url).isNotEmpty()) {
            val mangaId = checkChapterLists(url)
            return client.newCall(GET("$baseUrl/thong-tin-ca-nhan?manga_id=$mangaId", headers))
                .asObservableSuccess()
                .map { response -> chapterListParse(response) }
        }
        return super.fetchChapterList(manga)
    }

    private fun checkChapterLists(document: Document) = document.selectFirst("input[name=manga_id]")!!.attr("value")

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = searchURL.toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            if (page > 1) {
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

        if (loginRequired != null) {
            throw Exception("${loginRequired.text()} Hãy đăng nhập trong WebView.")
        }
        return document.select(".comicDetails img").mapIndexed { i, e ->
            Page(i, imageUrl = e.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = ""

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
