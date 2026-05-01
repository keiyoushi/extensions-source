package eu.kanade.tachiyomi.extension.vi.luvevaland

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class LuvEvaLand :
    HttpSource(),
    ConfigurableSource {

    override val name = "LuvEvaLand"

    override val lang = "vi"

    private val defaultBaseUrl = "https://luvevalands2.co"

    override val baseUrl get() = getPrefBaseUrl()

    override val supportsLatest = true

    private val preferences: SharedPreferences = getPreferences {
        getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != defaultBaseUrl) {
                edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

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

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/truyen-tranh", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("#total-tab-content .comic-item")
            .mapNotNull(::popularMangaFromElement)
            .distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun popularMangaFromElement(element: Element): SManga? {
        val mangaLinkElement = element.select("a[href*=/truyen-tranh/]")
            .firstOrNull {
                val href = it.absUrl("href")
                href.isNotEmpty() && !CHAPTER_URL_REGEX.containsMatchIn(href)
            }
            ?: return null

        val mangaTitle = element.selectFirst("a.comic-name")!!.text()

        return SManga.create().apply {
            title = mangaTitle
            setUrlWithoutDomain(mangaLinkElement.absUrl("href"))
            thumbnail_url = normalizeThumbnail(extractImageUrl(element.selectFirst(".comic-img img, img")))
        }
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/danh-sach-chuong-moi-cap-nhat".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".home__lg-book .book-vertical__item")
            .mapNotNull(::latestMangaFromElement)
            .distinctBy { it.url }

        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun latestMangaFromElement(element: Element): SManga? {
        val mangaLinkElement = element.selectFirst(".book__lg-title a[href*=/truyen-tranh/], .book__lg-image a[href*=/truyen-tranh/]")
            ?: return null

        val mangaUrl = mangaLinkElement.absUrl("href")
        if (!MANGA_PATH_REGEX.containsMatchIn(mangaUrl)) return null

        val mangaTitle = element.selectFirst(".book__lg-title a")!!.text()

        return SManga.create().apply {
            title = mangaTitle
            setUrlWithoutDomain(mangaUrl)
            thumbnail_url = normalizeThumbnail(extractImageUrl(element.selectFirst(".book__lg-image img, img")))
        }
    }

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("s", query)
                .build()
            return GET(url, headers)
        }

        val tagSlug = filters.firstInstanceOrNull<TagFilter>()?.toSlug()
        if (tagSlug != null) {
            val url = "$baseUrl/the-loai/$tagSlug".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .build()
            return GET(url, headers)
        }

        return GET("$baseUrl/tim-kiem?page=$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val requestUrl = response.request.url.toString()

        val mangas = if (requestUrl.contains("/the-loai/")) {
            document.select(".book-vertical__item")
                .mapNotNull(::latestMangaFromElement)
        } else {
            document.select("table.book__list tr.book__list-item")
                .mapNotNull(::searchMangaFromRow)
        }.distinctBy { it.url }

        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromRow(element: Element): SManga? {
        val linkElement = element.selectFirst("td.book__list-name a[href], td.book__list-image a[href]") ?: return null
        val mangaUrl = linkElement.absUrl("href")
        if (!MANGA_PATH_REGEX.containsMatchIn(mangaUrl)) return null

        val mangaTitle = element.selectFirst("td.book__list-name a")!!.text()

        return SManga.create().apply {
            title = mangaTitle
            setUrlWithoutDomain(mangaUrl)
            thumbnail_url = normalizeThumbnail(extractImageUrl(element.selectFirst("td.book__list-image img, img")))
        }
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val detailElement = document.selectFirst(".book__detail-container, .book__detail-contain, .comic-info")
        val titleElement = detailElement?.selectFirst(".book__detail-name, .comic-name-detail, .comic-name")
            ?: document.selectFirst(".book__detail-name, .comic-name-detail, .comic-name")
        val thumbnailElement = detailElement?.selectFirst(".book__detail-image img[alt], .comic-image img[alt], img[alt]")
            ?: document.selectFirst(".book__detail-image img[alt], .comic-image img[alt]")

        return SManga.create().apply {
            title = titleElement!!.text()
            thumbnail_url = normalizeThumbnail(extractImageUrl(thumbnailElement))
            author = detailElement?.selectFirst(".book__detail-text:matchesOwn((?i)^\\s*Tác giả:) a, .comic-author a")
                ?.text()
                ?: detailElement?.selectFirst(".book__detail-text:matchesOwn((?i)^\\s*Tác giả:), .comic-author")
                    ?.text()
                    ?.substringAfter(": ")
            status = parseStatus(
                detailElement?.selectFirst(".book__detail-text:matchesOwn((?i)^\\s*Tình trạng:), .comic-status-detail, .comic-status")?.text()
                    ?: document.selectFirst(".comic-status-detail, .comic-status")?.text(),
            )
            genre = (detailElement ?: document).select(".book__detail-text:matchesOwn((?i)^\\s*Tag:) a[href*=/the-loai/], a[href*=/the-loai/]")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = parseDescription(document)
        }
    }

    private fun parseDescription(document: Document): String? {
        val introPaneId = document.selectFirst("a[role=tab][href^=#]:matchesOwn((?i)GIỚI THIỆU)")
            ?.attr("href")

        val introElement = introPaneId?.let { document.selectFirst(it) }
            ?: document.selectFirst("#intro-tab-content, #comic-intro, .tab-content .tab-pane.active.in, .tab-content .tab-pane.active")

        return introElement?.text()?.ifEmpty { null }
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        statusText.contains("đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        statusText.contains("hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        statusText.contains("truyện full", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        var chapterRows = extractChapterRows(document)

        if (chapterRows.isEmpty() && response.request.url.encodedPath.contains("/un-lock")) {
            val unlockUrl = response.request.url.queryParameter("link")
            if (!unlockUrl.isNullOrBlank()) {
                val targetUrl = if (unlockUrl.startsWith("http")) unlockUrl else baseUrl + unlockUrl
                client.newCall(GET(targetUrl, headers)).execute().use { unlockResponse ->
                    document = unlockResponse.asJsoup()
                    chapterRows = extractChapterRows(document)
                }
            }
        }

        val rowChapters = chapterRows
            .mapNotNull(::chapterFromRow)
            .sortedByDescending { it.first }
            .map { it.second }

        if (rowChapters.isNotEmpty()) return rowChapters
        return emptyList()
    }

    private fun extractChapterRows(document: Document): List<Element> = document
        .select("table.list-chapter tbody tr, table.list-chapter__container tbody tr, .chapter-list-inner tr, tr.sort-item")
        .ifEmpty { document.select("table tr") }

    private fun chapterFromRow(element: Element): Pair<Int, SChapter>? {
        val chapterNameElement = element.selectFirst("td.list-chapter__name a, td:first-child a") ?: return null

        val chapterLinkElement = element.selectFirst("a[href*=/chap], a[href*=/chuong], a[href*=/chapter], a[href*=/mo-khoa/chap]") ?: return null
        val chapterUrl = chapterLinkElement.absUrl("href")
        if (!CHAPTER_URL_REGEX.containsMatchIn(chapterUrl)) return null

        val chapterName = chapterNameElement.ownText().ifEmpty { chapterNameElement.text() }

        val isLocked =
            chapterNameElement.attr("href").startsWith("javascript") ||
                element.selectFirst("td.list-chapter__cost img[src*=lock], td.list-chapter__cost img[alt*=khóa], td.list-chapter__cost .chapter-icon") != null

        val chapterOrder = element.attr("data-order").toIntOrNull()
            ?: CHAPTER_NUMBER_REGEX.find(chapterUrl)?.groupValues?.get(1)?.toIntOrNull()
            ?: 0

        val chapterDate = element.selectFirst("td.list-chapter__date, td:last-child")
            ?.text()
            ?.let { DATE_FORMAT.tryParse(it) }
            ?: 0L

        return chapterOrder to SChapter.create().apply {
            name = if (isLocked && !isAutoUnlockEnabled) "🔒 $chapterName" else chapterName
            setUrlWithoutDomain(chapterUrl)
            date_upload = chapterDate
        }
    }

    // ============================== Pages =================================

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = client.newCall(pageListRequest(chapter)).asObservable().map { response ->
        parsePageList(response, chapter.url)
    }

    private fun parsePageList(response: Response, chapterUrl: String): List<Page> {
        val document = response.asJsoup()

        val images = document.select("#view-chapter img, #chapter-content img, .chapter-content img, .reading-content img, .content-chapter img, .box-chapter-content img")
            .map { it.absUrl("data-src").ifEmpty { it.absUrl("src") } }
            .filter { it.isNotBlank() && !it.startsWith("data:image") }

        if (images.isNotEmpty()) {
            return images.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
        }

        if (isAutoUnlockEnabled) {
            return buildPageListFromPattern(chapterUrl)
        }

        throw Exception(LOGIN_WEBVIEW_MESSAGE)
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    /**
     * Build page list by probing predictable CDN image URLs.
     * Extracts manga slug from the chapter URL and probes common CDN patterns
     * with HEAD requests until a non-image response.
     */
    private fun buildPageListFromPattern(chapterUrl: String): List<Page> {
        val mangaSlug = MANGA_SLUG_REGEX.find(chapterUrl)?.groupValues?.get(1)
            ?: throw Exception("Không tìm thấy slug truyện")
        val chapterNum = CHAPTER_NUMBER_REGEX.find(chapterUrl)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw Exception("Không tìm thấy số chương")

        val cdnInfo = probeCdnPattern(mangaSlug, chapterNum)
            ?: throw Exception("Không tìm thấy hình ảnh CDN cho chương này")

        val pages = mutableListOf<Page>()
        var index = 1

        while (index <= 200) {
            val imageUrl = "${cdnInfo.basePath}/${cdnInfo.chapterPrefix}$chapterNum/$index.${cdnInfo.extension}"
            val headRequest = Request.Builder().url(imageUrl).head().build()
            val isImage = client.newCall(headRequest).execute().use {
                it.isSuccessful && it.header("Content-Type")?.startsWith("image/") == true
            }

            if (!isImage) break

            pages.add(Page(index - 1, imageUrl = imageUrl))
            index++
        }

        if (pages.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh cho chương khóa")
        }

        return pages
    }

    private data class CdnInfo(
        val basePath: String,
        val chapterPrefix: String,
        val extension: String,
    )

    private fun probeCdnPattern(mangaSlug: String, chapterNum: Int): CdnInfo? {
        val patterns = listOf(
            CdnInfo("$CDN_BASE_URL/$mangaSlug", "c", "png"),
            CdnInfo("$CDN_BASE_URL/$mangaSlug", "c", "jpg"),
            CdnInfo("$CDN_BASE_URL/$mangaSlug", "", "png"),
            CdnInfo("$CDN_BASE_URL/$mangaSlug", "", "jpg"),
        )

        return patterns.firstOrNull { cdnInfo ->
            val testUrl = "${cdnInfo.basePath}/${cdnInfo.chapterPrefix}$chapterNum/1.${cdnInfo.extension}"
            val headRequest = Request.Builder().url(testUrl).head().build()
            client.newCall(headRequest).execute().use {
                it.isSuccessful && it.header("Content-Type")?.startsWith("image/") == true
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Helpers ===============================

    private fun extractImageUrl(element: Element?): String? {
        if (element == null) return null

        val imageUrl = element.absUrl("data-src")
            .ifEmpty { element.absUrl("src") }

        return imageUrl.ifEmpty { null }
    }

    private fun normalizeThumbnail(url: String?): String? {
        if (url == null) return null
        return url.replace(THUMBNAIL_SIZE_REGEX, "")
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = KEY_AUTO_UNLOCK_CHAPTERS
            title = "Tự mở khóa chương"
            summary = "Có thể gây chậm hoặc crash cân nhắc khi sử dụng."
            setDefaultValue(false)
        }.let(screen::addPreference)

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

    private val isAutoUnlockEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTO_UNLOCK_CHAPTERS, false)

    companion object {
        private const val LOGIN_WEBVIEW_MESSAGE = "Vui lòng đăng nhập vào tài khoản phù hợp để xem chương này"

        private const val KEY_AUTO_UNLOCK_CHAPTERS = "autoUnlockChapters"

        private val WEBVIEW_TOKEN_REGEX = Regex("""\;\s*wv\)""")
        private val MANGA_PATH_REGEX = Regex("""/truyen-tranh/""")
        private const val CDN_BASE_URL = "https://picevaland.xyz/cloud"
        private val MANGA_SLUG_REGEX = Regex("""/truyen-tranh/([^/.]+)""")
        private val CHAPTER_URL_REGEX = Regex("""/(?:chap|chuong|chapter|mo-khoa/chap)""", RegexOption.IGNORE_CASE)
        private val CHAPTER_NUMBER_REGEX = Regex("""/(?:chap|chuong|chapter)-([0-9]+)""", RegexOption.IGNORE_CASE)
        private val THUMBNAIL_SIZE_REGEX = Regex("""-[0-9]+x[0-9]+(?=\.(?:jpe?g|png|webp)$)""")

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }

        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF_SUMMARY = "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
    }
}
