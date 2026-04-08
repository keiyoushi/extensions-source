package eu.kanade.tachiyomi.extension.vi.luottruyen

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.util.Calendar
import java.util.TimeZone

class LuotTruyen :
    HttpSource(),
    ConfigurableSource {

    override val name = "LuotTruyen"

    override val lang = "vi"

    private val defaultBaseUrl = "https://luottruyen4.com"

    override val baseUrl get() = getPrefBaseUrl()

    override val supportsLatest = true

    override val client = network.cloudflareClient

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

    // Strip "wv" from User-Agent so Google login works in this source.
    // Google deny login when User-Agent contains the WebView token.
    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .apply {
            build()["user-agent"]?.let { userAgent ->
                set("user-agent", removeWebViewToken(userAgent))
            }
        }

    private fun removeWebViewToken(userAgent: String): String = userAgent.replace(WEBVIEW_TOKEN_REGEX, ")")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/tim-truyen?status=-1&sort=10" + if (page > 1) "&page=$page" else "", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select("div.item").map { element ->
            SManga.create().apply {
                val linkElement: Element = element.selectFirst("figcaption h3 a, a.jtip")!!
                title = linkElement.text()
                setUrlWithoutDomain(linkElement.absUrl("href"))
                thumbnail_url = element.selectFirst("div.image a img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("li.next:not(.disabled) a, li:not(.disabled).next a") != null

        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?page=$page&typegroup=0", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select("#ctl00_divCenter .row > .item").map { element ->
            SManga.create().apply {
                val linkElement: Element = element.selectFirst("figcaption h3 a, a.jtip")!!
                title = linkElement.text()
                setUrlWithoutDomain(linkElement.absUrl("href"))
                thumbnail_url = element.selectFirst("div.image a img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("li.next:not(.disabled) a, li:not(.disabled).next a") != null

        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-truyen".toHttpUrl().newBuilder().apply {
                addQueryParameter("keyword", query)
                if (page > 1) addQueryParameter("page", page.toString())
            }.build()
            return GET(url, headers)
        }

        val genreSlug = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
        val sortValue = filters.firstInstanceOrNull<SortFilter>()?.toUriPart()
        val statusValue = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()

        if (genreSlug != null) {
            val url = "$baseUrl/tim-truyen/$genreSlug".toHttpUrl().newBuilder().apply {
                if (page > 1) addQueryParameter("page", page.toString())
            }.build()
            return GET(url, headers)
        }

        val url = "$baseUrl/tim-truyen".toHttpUrl().newBuilder().apply {
            if (sortValue != null) {
                addQueryParameter("sort", sortValue)
            }
            if (statusValue != null) {
                addQueryParameter("status", statusValue)
            }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("article#item-detail h1.title-detail, article#item-detail h1, h1.title-detail")!!.text()
            val infoElement: Element? = document.selectFirst("article#item-detail")
            if (infoElement != null) {
                author = infoElement.selectFirst("li.author p.col-xs-8")?.text()
                status = infoElement.selectFirst("li.status p.col-xs-8")?.text().toStatus()
                genre = infoElement.select("li.kind p.col-xs-8 a").joinToString { it.text() }
                description = infoElement.select("div.detail-content p").joinToString("\n") { it.text() }
                thumbnail_url = infoElement.selectFirst("div.col-image img")?.absUrl("src")
            }
        }
    }

    private fun String?.toStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        this.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        this.contains("Đang cập nhật", ignoreCase = true) -> SManga.ONGOING
        this.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val storyId = manga.url.substringAfterLast("-")

        val formBody = FormBody.Builder()
            .add("StoryID", storyId)
            .build()

        val chapterHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .build()

        return POST("$baseUrl/Story/ListChapterByStoryID", chapterHeaders, formBody)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("li.row:not(.heading)").map { element ->
            SChapter.create().apply {
                val chapterLinkElement: Element? = element.selectFirst("div.chapter a, a")
                if (chapterLinkElement != null) {
                    name = chapterLinkElement.text()
                    setUrlWithoutDomain(chapterLinkElement.absUrl("href"))
                }
                date_upload = parseRelativeDate(element.selectFirst("div.col-xs-4")?.text())
            }
        }
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        val number = RELATIVE_DATE_NUMBER_REGEX.find(dateStr)?.value?.toIntOrNull() ?: return 0L

        when {
            dateStr.contains("giây") -> calendar.add(Calendar.SECOND, -number)
            dateStr.contains("phút") -> calendar.add(Calendar.MINUTE, -number)
            dateStr.contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            dateStr.contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            dateStr.contains("tuần") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            dateStr.contains("tháng") -> calendar.add(Calendar.MONTH, -number)
            dateStr.contains("năm") -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val images = document.select("#view-chapter img")
            .ifEmpty {
                document.select(".chapter-content img, .reading-content img, .content-chapter img, .reading-detail .page-chapter img[data-index]")
            }

        if (images.isEmpty()) {
            val hasLoginHint = document.selectFirst(
                "a[href*='/Account/Login'], a[href*='/dang-nhap'], a[href*='returnUrl='], .login-page-wrapper",
            ) != null ||
                document.title().contains("đăng nhập", ignoreCase = true) ||
                document.title().contains("login", ignoreCase = true)
            if (hasLoginHint) {
                throw Exception(LOGIN_WEBVIEW_MESSAGE)
            }
            throw Exception("Không tìm thấy hình ảnh")
        }

        return images.mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("src")) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(defaultBaseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultBaseUrl"
        }.let(screen::addPreference)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

    companion object {
        private val WEBVIEW_TOKEN_REGEX = Regex(""";\s*wv\)""")
        private val RELATIVE_DATE_NUMBER_REGEX = Regex("\\d+")
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF_SUMMARY = "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
        private const val LOGIN_WEBVIEW_MESSAGE = "Vui lòng đăng nhập bằng Webview để xem chương này"
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()
}
