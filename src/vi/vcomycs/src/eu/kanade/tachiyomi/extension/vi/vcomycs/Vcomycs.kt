package eu.kanade.tachiyomi.extension.vi.vcomycs

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

class Vcomycs :
    HttpSource(),
    ConfigurableSource {
    override val name = "Vcomycs"
    override val lang = "vi"
    override val supportsLatest = true

    private val defaultBaseUrl = "https://vivicomi19.info"
    private val preferences: SharedPreferences = getPreferences()

    override val baseUrl get() = getPrefBaseUrl()

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

    private val thumbnailFallbackInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val fallbackUrl = thumbFallbackMap.remove(request.url.toString()) ?: return@Interceptor response

        val isBadCode = (response.code == 401 || response.code == 404)
        if (!isBadCode) {
            return@Interceptor response
        }

        response.close()

        val fallbackRequest = GET(fallbackUrl, request.headers)
        chain.proceed(fallbackRequest)
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .addInterceptor(thumbnailFallbackInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ========================= Popular ===========================

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/nhieu-xem-nhat/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.most-views.single-list-comic li.position-relative").map { element ->
            SManga.create().apply {
                val linkElement = element.selectFirst("p.super-title a")!!
                title = linkElement.text()
                setUrlWithoutDomain(linkElement.absUrl("href"))
                thumbnail_url = element.selectFirst("img.list-left-img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ========================= Latest ============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page/"
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".col-md-3.col-xs-6.comic-item").map { element ->
            SManga.create().apply {
                val linkElement = element.selectFirst("h3.comic-title")!!.parent()!!
                title = element.selectFirst("h3.comic-title")!!.text()
                setUrlWithoutDomain(linkElement.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("ul.pager li.next:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Search ============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val formBody = FormBody.Builder()
                .add("action", "searchtax")
                .add("keyword", query)
                .build()
            return POST("$baseUrl/wp-admin/admin-ajax.php", headers, formBody)
        }

        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.toUriPart()
        if (!genre.isNullOrBlank()) {
            val url = if (page == 1) "$baseUrl/$genre/" else "$baseUrl/$genre/page/$page/"
            return GET(url, headers)
        }

        return latestUpdatesRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val contentType = response.header("Content-Type", "")
        if (contentType?.contains("application/json") == true) {
            return parseSearchApiResponse(response)
        }
        return latestUpdatesParse(response)
    }

    private fun parseSearchApiResponse(response: Response): MangasPage {
        val searchResponse = response.parseAs<SearchResponse>()

        val mangas = searchResponse.data
            .filter { it.link.contains("/truyen-tranh/") } // Only manga, exclude news
            .map { result ->
                SManga.create().apply {
                    title = result.title
                    setUrlWithoutDomain(result.link.removePrefix(baseUrl))
                    thumbnail_url = resolveSearchThumbnailUrl(result.img)
                }
            }.distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun resolveSearchThumbnailUrl(url: String?): String? {
        if (url.isNullOrBlank() || !url.contains("-150x150")) return url

        val removed = url.replace("-150x150", "")
        val replaced = url.replace("-150x150", "-720x970")
        thumbFallbackMap[removed] = replaced
        return removed
    }

    // ========================= Details ===========================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h2.info-title")!!.text()
            thumbnail_url = document.selectFirst("img.info-cover")?.absUrl("src")
            author = document.selectFirst("strong:contains(Tác giả) + span")?.text()
            status = document.selectFirst("span.comic-stt")?.text()
                ?.let { parseStatus(it) }
                ?: SManga.UNKNOWN
            genre = document.select("a[href*=/the-loai/]")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = parseDescription(document)
        }
    }

    private fun parseDescription(document: Document): String? {
        val block = document.selectFirst(".intro-container .hide-long-text")
            ?: document.selectFirst(".intro-container > p")
            ?: return null

        val ownText = block.ownText().trim()
        val rawDescription = (if (ownText.isNotEmpty()) ownText else block.text())
            .substringBefore("— Xem Thêm —")
            .substringBefore("- Xem thêm -")
            .trim()

        return rawDescription
            .removePrefix("\"")
            .removeSuffix("\"")
            .trim()
            .takeUnless {
                it.isEmpty() ||
                    it.equals("Đang cập nhật", ignoreCase = true) ||
                    it.equals("Đang cập nhật...", ignoreCase = true) ||
                    it.equals("Không có", ignoreCase = true)
            }
    }

    private fun parseStatus(status: String): Int = when {
        status.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        status.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ========================= Chapters ==========================

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()

        chapters += document.select(".chapter-table table tbody tr")
            .mapNotNull(::parseChapterElement)

        val visited = mutableSetOf(response.request.url.toString())
        var nextPage = document.selectFirst("ul.pager li.next:not(.disabled) a")
            ?.absUrl("href")
            ?.ifEmpty { null }

        while (nextPage != null && visited.add(nextPage)) {
            client.newCall(GET(nextPage, headers)).execute().use { pageResponse ->
                document = pageResponse.asJsoup()
                chapters += document.select(".chapter-table table tbody tr")
                    .mapNotNull(::parseChapterElement)
                nextPage = document.selectFirst("ul.pager li.next:not(.disabled) a")
                    ?.absUrl("href")
                    ?.ifEmpty { null }
            }
        }

        return chapters
    }

    private fun parseChapterElement(element: Element): SChapter? {
        val linkElement = element.selectFirst("a.text-capitalize") ?: return null
        val url = linkElement.absUrl("href")
        if (url.isEmpty()) return null

        val isLocked = linkElement.selectFirst(".glyphicon-lock, .fa-lock, .icon-lock") != null

        return SChapter.create().apply {
            setUrlWithoutDomain(url)
            val fullText = linkElement.selectFirst("span.hidden-sm.hidden-xs")?.text()
                ?: linkElement.text()
            val shortName = parseChapterName(fullText)
            name = if (isLocked) "🔒 $shortName" else shortName

            date_upload = element.selectFirst("td.hidden-xs.hidden-sm, td:last-child")
                ?.text()
                ?.let(::parseChapterDate)
                ?: 0L
        }
    }

    private fun parseChapterName(rawName: String): String {
        val match = CHAPTER_NAME_REGEX.find(rawName)
        if (match != null) {
            return match.value
                .replace(CHAPTER_WORD_REGEX, "CHAP")
                .replace(MULTI_SPACE_REGEX, " ")
                .trim()
        }

        return rawName.substringAfterLast("–").substringAfterLast("-").trim()
    }

    private fun parseChapterDate(dateStr: String): Long {
        val cleanDate = dateStr.trim()
        val shortYear = DATE_FORMAT_SHORT.tryParse(cleanDate)
        if (shortYear != 0L) {
            return shortYear
        }
        return DATE_FORMAT_FULL.tryParse(cleanDate)
    }

    // ========================= Pages =============================

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val document = Jsoup.parse(html, response.request.url.toString())

        if (document.selectFirst("form.post-password-form input[name=post_password], input[name=post_password]") != null) {
            throw Exception(PASSWORD_WEBVIEW_MESSAGE)
        }

        val imageUrls = ImageDecryptor.extractImageUrls(html)

        if (imageUrls.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return imageUrls.mapIndexed { idx, url ->
            Page(idx, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = getFilters()

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

        private const val PASSWORD_WEBVIEW_MESSAGE = "Vui lòng nhập mật khẩu của chương này qua webview"
        private val thumbFallbackMap = ConcurrentHashMap<String, String>()
        private val CHAPTER_NAME_REGEX = Regex("chap\\s*\\d+(?:\\.\\d+)?", RegexOption.IGNORE_CASE)
        private val CHAPTER_WORD_REGEX = Regex("chap", RegexOption.IGNORE_CASE)
        private val MULTI_SPACE_REGEX = Regex("\\s+")

        private val DATE_FORMAT_FULL by lazy {
            SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }

        private val DATE_FORMAT_SHORT by lazy {
            SimpleDateFormat("dd/MM/yy", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }
    }
}
