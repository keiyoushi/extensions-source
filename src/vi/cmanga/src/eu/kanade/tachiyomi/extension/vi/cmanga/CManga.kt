package eu.kanade.tachiyomi.extension.vi.cmanga

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CManga :
    HttpSource(),
    ConfigurableSource {

    override val name = "CManga"

    override val lang = "vi"

    private val defaultBaseUrl = "https://cmangax16.com"

    override val baseUrl get() = getPrefBaseUrl()

    override val supportsLatest = true

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

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
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

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/home_album_list".toHttpUrl().newBuilder()
            .addQueryParameter("file", SOURCE_FILE)
            .addQueryParameter("type", "hot")
            .addQueryParameter("sort", DEFAULT_SORT)
            .addQueryParameter("tag", "")
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/home_album_list".toHttpUrl().newBuilder()
            .addQueryParameter("file", SOURCE_FILE)
            .addQueryParameter("type", "update")
            .addQueryParameter("sort", DEFAULT_SORT)
            .addQueryParameter("tag", "")
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genres = filters.firstInstanceOrNull<GenreFilter>()
            ?.selectedValues()
            .orEmpty()
            .joinToString(",")

        val team = filters.firstInstanceOrNull<TeamFilter>()
            ?.toUriPart()
            ?: "0"
        val minChapter = filters.firstInstanceOrNull<MinChapterFilter>()
            ?.toUriPart()
            ?: "0"
        val sort = filters.firstInstanceOrNull<SortFilter>()
            ?.toUriPart()
            ?: DEFAULT_SORT
        val status = filters.firstInstanceOrNull<StatusFilter>()
            ?.toUriPart()
            ?: "all"

        val url = "$baseUrl/api/home_album_list".toHttpUrl().newBuilder()
            .addQueryParameter("file", SOURCE_FILE)
            .addQueryParameter("type", "search")
            .addQueryParameter("sort", sort)
            .addQueryParameter("tag", genres)
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("status", status)
            .addQueryParameter("string", query)
            .addQueryParameter("team", team)
            .addQueryParameter("num_chapter", minChapter)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    private fun parseMangaPage(response: Response): MangasPage {
        val payload = response.parseAs<CMangaAlbumListResponse>()
        val mangas = payload.data?.data
            .orEmpty()
            .mapNotNull(::toSManga)

        val total = payload.data?.total ?: 0
        val hasNextPage = hasNextPage(total, response.request.url.queryParameter("page"), response.request.url.queryParameter("limit"))
        return MangasPage(mangas, hasNextPage)
    }

    private fun toSManga(item: CMangaAlbumItem): SManga? {
        val info = parseAlbumInfo(item.info) ?: return null
        val title = info.name ?: return null
        val slug = info.url ?: return null
        val id = item.idAlbum ?: return null

        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain("/album/$slug-$id")
            thumbnail_url = resolveCoverUrl(info.avatar)
        }
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val albumId = extractAlbumId(response.request.url.encodedPath)
        val apiInfo = albumId?.let(::fetchAlbumInfo)

        return SManga.create().apply {
            title = document.selectFirst("div.book_other h1 .name, div.book_other h1 p.name")!!.text()
            thumbnail_url = document.selectFirst("div.book_avatar img[itemprop=image], div.book_avatar img")?.absUrl("src")
                ?: resolveCoverUrl(apiInfo?.avatar)
            author = apiInfo?.author?.joinToString().ifNullOrBlank { "Unknown" }
            status = parseStatus(apiInfo?.status)
            genre = apiInfo?.tags
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString()
            description = parseDescription(document, apiInfo?.detail)
        }
    }

    private fun fetchAlbumInfo(albumId: String): CMangaAlbumInfo? {
        val url = "$baseUrl/api/get_data_by_id".toHttpUrl().newBuilder()
            .addQueryParameter("id", albumId)
            .addQueryParameter("table", "album")
            .addQueryParameter("data", "info,data")
            .build()

        return client.newCall(GET(url, headers)).execute().use { response ->
            if (!response.isSuccessful) return null
            val payload = response.parseAs<CMangaAlbumByIdResponse>()
            parseAlbumInfo(payload.data?.info)
        }
    }

    private fun parseDescription(document: Document, apiDetail: String?): String? {
        val html = document.selectFirst("#book_detail_text")?.html() ?: apiDetail
        if (html == null) return null

        val normalized = html
            .replace(BR_TAG_REGEX, "\n")
            .replace("&nbsp;", " ")

        val plainText = Jsoup.parse(normalized).wholeText()
            .replace(XEM_THEM_REGEX, "")
            .replace(AN_BOT_REGEX, "")
            .replace(HORIZONTAL_SPACE_REGEX, " ")
            .replace(MULTI_NEWLINE_REGEX, "\n")
            .trim()

        return plainText.ifEmpty { null }
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("doing", ignoreCase = true) -> SManga.ONGOING
        status.contains("done", ignoreCase = true) -> SManga.COMPLETED
        status.contains("đang", ignoreCase = true) -> SManga.ONGOING
        status.contains("hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val albumId = extractAlbumId(manga.url) ?: throw Exception("Không tìm thấy mã truyện")
        val albumSlug = extractAlbumSlug(manga.url)
        val version = currentEpochSeconds()

        return chapterListPageRequest(albumId, 1, albumSlug, version)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val albumId = response.request.url.queryParameter("album") ?: throw Exception("Không tìm thấy mã truyện")
        val albumSlug = response.request.url.queryParameter("slug")
        val version = response.request.url.queryParameter("v") ?: currentEpochSeconds()
        var page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1

        val seenChapterIds = mutableSetOf<String>()
        val chapters = mutableListOf<SChapter>()

        var pageItems = response.parseAs<CMangaChapterListResponse>().data.orEmpty()
        chapters += toSChapterList(pageItems, albumSlug, seenChapterIds)

        while (pageItems.size >= CHAPTER_PAGE_SIZE) {
            page += 1
            val request = chapterListPageRequest(albumId, page, albumSlug, version)
            client.newCall(request).execute().use { nextResponse ->
                pageItems = nextResponse.parseAs<CMangaChapterListResponse>().data.orEmpty()
            }

            if (pageItems.isEmpty()) break

            val newItems = toSChapterList(pageItems, albumSlug, seenChapterIds)
            if (newItems.isEmpty()) break
            chapters += newItems
        }

        return chapters
    }

    private fun chapterListPageRequest(albumId: String, page: Int, slug: String?, version: String): Request {
        val url = "$baseUrl/api/chapter_list".toHttpUrl().newBuilder()
            .addQueryParameter("album", albumId)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", CHAPTER_PAGE_SIZE.toString())
            .addQueryParameter("v", version)
            .apply {
                if (slug != null) {
                    addQueryParameter("slug", slug)
                }
            }
            .build()
        return GET(url, headers)
    }

    private fun toSChapterList(
        chapterItems: List<CMangaChapterItem>,
        albumSlug: String?,
        seenChapterIds: MutableSet<String>,
    ): List<SChapter> {
        return chapterItems.mapNotNull { chapterItem ->
            val chapterInfo = parseChapterInfo(chapterItem.info) ?: return@mapNotNull null
            val chapterId = chapterInfo.id.asStringOrNull() ?: chapterItem.idChapter?.toString() ?: return@mapNotNull null
            if (!seenChapterIds.add(chapterId)) return@mapNotNull null

            val chapterNumber = chapterInfo.num.asStringOrNull() ?: return@mapNotNull null
            val chapterTitle = buildChapterTitle(chapterNumber, chapterInfo.name)
            val chapterName = if (isChapterLocked(chapterInfo)) "🔒 $chapterTitle" else chapterTitle
            val slug = albumSlug ?: "truyen"

            SChapter.create().apply {
                name = chapterName
                setUrlWithoutDomain("/album/$slug/chapter-$chapterNumber-$chapterId")
                date_upload = chapterInfo.lastUpdate?.let { DATE_FORMAT.tryParse(it) } ?: 0L
            }
        }
    }

    private fun buildChapterTitle(number: String, chapterTitle: String?): String {
        if (chapterTitle.isNullOrBlank()) return "Chapter $number"
        if (isRedundantChapterTitle(number, chapterTitle)) return "Chapter $number"
        return "Chapter $number: $chapterTitle"
    }

    private fun isRedundantChapterTitle(number: String, chapterTitle: String): Boolean {
        val normalizedNumber = number.lowercase(Locale.ROOT)
        val normalizedTitle = chapterTitle.lowercase(Locale.ROOT).removeSuffix(":")
        if (
            normalizedTitle == normalizedNumber ||
            normalizedTitle == "chapter $normalizedNumber" ||
            normalizedTitle == "chap $normalizedNumber" ||
            normalizedTitle == "chương $normalizedNumber" ||
            normalizedTitle == "chuong $normalizedNumber"
        ) {
            return true
        }

        val compactNumber = normalizedNumber.replace(" ", "")
        val compactTitle = normalizedTitle.replace(" ", "")

        for (prefix in REDUNDANT_CHAPTER_PREFIXES) {
            if (!compactTitle.startsWith(prefix)) continue

            val rest = compactTitle.removePrefix(prefix)
            if (!rest.startsWith(compactNumber)) continue

            val suffix = rest.removePrefix(compactNumber)
            if (suffix.isEmpty() || !suffix.first().isDigit()) {
                return true
            }
        }

        return false
    }

    private fun isChapterLocked(chapterInfo: CMangaChapterInfo): Boolean {
        val lock = chapterInfo.lock
        val chapterLevel = chapterInfo.level.asIntOrNull() ?: 0
        val lockLevel = lock?.level.asIntOrNull() ?: 0
        val lockFee = lock?.fee.asIntOrNull() ?: 0
        val nowSeconds = System.currentTimeMillis() / 1000
        val hasActiveLock = lock != null && (lock.end.asLongOrNull() ?: 0L) >= nowSeconds
        return hasActiveLock || chapterLevel != 0 || lockLevel != 0 || lockFee != 0
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val chapterId = extractChapterId(response.request.url.encodedPath)
            ?: throw Exception("Không tìm thấy mã chương")
        val userSecurity = getUserSecurity()

        val url = "$baseUrl/api/chapter_image".toHttpUrl().newBuilder()
            .addQueryParameter("chapter", chapterId)
            .addQueryParameter("v", "0")
            .addQueryParameter("time", (System.currentTimeMillis() / 1000).toString())
            .addQueryParameter("user_id", userSecurity.id ?: "0")
            .addQueryParameter("user_token", userSecurity.token ?: "")
            .build()

        return client.newCall(GET(url, headers)).execute().use { imageResponse ->
            if (!imageResponse.isSuccessful) throw Exception("Không tìm thấy hình ảnh")

            val payload = imageResponse.parseAs<CMangaChapterImageResponse>()
            val imageData = payload.data ?: throw Exception("Không tìm thấy hình ảnh")
            if (imageData.status != 1) {
                throw Exception(LOGIN_WEBVIEW_MESSAGE)
            }

            val images = imageData.image.orEmpty()
            if (images.isEmpty()) {
                throw Exception("Không tìm thấy hình ảnh")
            }

            images.mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Helpers ===============================

    private fun parseAlbumInfo(rawInfo: String?): CMangaAlbumInfo? {
        if (rawInfo == null) return null
        return runCatching { rawInfo.parseAs<CMangaAlbumInfo>() }.getOrNull()
    }

    private fun parseChapterInfo(rawInfo: String?): CMangaChapterInfo? {
        if (rawInfo == null) return null
        return runCatching { rawInfo.parseAs<CMangaChapterInfo>() }.getOrNull()
    }

    private fun resolveCoverUrl(avatar: String?): String? {
        if (avatar.isNullOrBlank()) return null
        if (avatar.startsWith("http://") || avatar.startsWith("https://")) return avatar
        if (avatar.startsWith("/")) return "$baseUrl$avatar"
        return "$baseUrl/assets/tmp/album/$avatar"
    }

    private fun hasNextPage(total: Int, page: String?, limit: String?): Boolean {
        val currentPage = page?.toIntOrNull() ?: 1
        val pageSize = limit?.toIntOrNull() ?: PAGE_SIZE
        return currentPage * pageSize < total
    }

    private fun extractAlbumId(url: String): String? = ALBUM_ID_REGEX.find(url)?.groupValues?.get(1)

    private fun extractAlbumSlug(url: String): String? = ALBUM_SLUG_REGEX.find(url)?.groupValues?.get(1)

    private fun extractChapterId(urlPath: String): String? = CHAPTER_ID_REGEX.find(urlPath)?.groupValues?.get(1)

    private fun String?.ifNullOrBlank(defaultValue: () -> String): String {
        if (this.isNullOrBlank()) return defaultValue()
        return this
    }

    private class CMangaUserSecurityCredential(
        val id: String? = null,
        val token: String? = null,
    )

    private fun getUserSecurity(): CMangaUserSecurityCredential {
        val cookieValue = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            .firstOrNull { it.name == USER_SECURITY_COOKIE }
            ?.value
            ?: return CMangaUserSecurityCredential()

        val decoded = decodeCookieValue(cookieValue) ?: return CMangaUserSecurityCredential()
        val security = runCatching { decoded.parseAs<CMangaUserSecurity>() }.getOrNull()
            ?: return CMangaUserSecurityCredential()

        return CMangaUserSecurityCredential(
            id = security.id.asStringOrNull(),
            token = security.token.asStringOrNull(),
        )
    }

    private fun decodeCookieValue(value: String): String? {
        var decoded = value
        repeat(2) {
            val next = runCatching { URLDecoder.decode(decoded, StandardCharsets.UTF_8.name()) }.getOrNull()
                ?: return null
            if (next == decoded) return decoded
            decoded = next
        }
        return decoded
    }

    private fun currentEpochSeconds(): String = (System.currentTimeMillis() / 1000).toString()

    private fun JsonElement?.asStringOrNull(): String? = (this as? JsonPrimitive)?.content

    private fun JsonElement?.asIntOrNull(): Int? = asStringOrNull()?.toIntOrNull()

    private fun JsonElement?.asLongOrNull(): Long? = asStringOrNull()?.toLongOrNull()

    // ============================== Settings ==============================

    override fun getFilterList(): FilterList = getFilters()

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
        private const val SOURCE_FILE = "image"
        private const val DEFAULT_SORT = "update"
        private const val PAGE_SIZE = 20
        private const val CHAPTER_PAGE_SIZE = 50
        private const val LOGIN_WEBVIEW_MESSAGE = "Vui lòng đăng nhập vào tài khoản phù hợp qua Webview để đọc chương này"

        private val WEBVIEW_TOKEN_REGEX = Regex(""";\s*wv\)""")
        private val ALBUM_ID_REGEX = Regex("""-([0-9]+)(?:/ref/[0-9]+)?/?$""")
        private val ALBUM_SLUG_REGEX = Regex("""/album/([^/]+?)-[0-9]+(?:/ref/[0-9]+)?/?$""")
        private val CHAPTER_ID_REGEX = Regex("""chapter-[^/]+-([0-9]+)""")
        private val BR_TAG_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
        private val HORIZONTAL_SPACE_REGEX = Regex("[\\t\\x0B\\f\\r ]+")
        private val MULTI_NEWLINE_REGEX = Regex("\\n{2,}")
        private val XEM_THEM_REGEX = Regex("""\.\.\.\s*Xem thêm""", RegexOption.IGNORE_CASE)
        private val AN_BOT_REGEX = Regex("""Ẩn bớt""", RegexOption.IGNORE_CASE)

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }

        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF_SUMMARY = "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
        private const val USER_SECURITY_COOKIE = "user_security"

        private val REDUNDANT_CHAPTER_PREFIXES = listOf("chapter", "chap", "chương", "chuong")
    }
}
