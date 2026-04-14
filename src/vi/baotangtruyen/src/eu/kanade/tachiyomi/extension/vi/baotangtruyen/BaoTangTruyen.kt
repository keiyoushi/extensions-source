package eu.kanade.tachiyomi.extension.vi.baotangtruyen

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BaoTangTruyen : HttpSource() {
    override val name = "BaoTangTruyen"
    override val lang = "vi"
    override val baseUrl = "https://baotangtruyen37.top"
    override val supportsLatest = true

    private val apiUrl = "https://api.chilltruyentranh.site"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/getAllComics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("sort", "views")
            .addQueryParameter("genres", "Tất cả")
            .addQueryParameter("is_leech", "false")
            .build()
        return GET(url, authHeaders())
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/getAllComics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("sort", "created_at")
            .addQueryParameter("genres", "Tất cả")
            .addQueryParameter("is_leech", "false")
            .build()
        return GET(url, authHeaders())
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "created_at"

        val urlBuilder = "$apiUrl/getAllComics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("is_leech", "false")

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("name", query)
        } else {
            urlBuilder.addQueryParameter("sort", sort)
            urlBuilder.addQueryParameter("genres", "Tất cả")
        }

        return GET(urlBuilder.build(), authHeaders())
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Details ===============================

    private fun extractMangaSlug(url: String): String? = MANGA_SLUG_REGEX.find(url)?.groupValues?.getOrNull(1)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = extractMangaSlug(manga.url)
            ?: throw Exception("Không tìm thấy truyện")

        val url = "$apiUrl/comic/$slug".toHttpUrl().newBuilder().build()
        return GET(url, authHeaders())
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val comic = response.parseAs<ComicDto>()

        return SManga.create().apply {
            title = comic.name ?: throw Exception("Không tìm thấy tên truyện")
            thumbnail_url = thumbnailUrl(comic.thumbnail ?: comic.hotThumbnail)
            author = comic.author
            status = parseStatus("Đang cập nhật")
            genre = parseGenres(comic.genres).joinToString().ifEmpty { null }
            description = parseDescription(comic.description)
        }
    }

    private fun parseDescription(rawDescription: String?): String? {
        if (rawDescription.isNullOrBlank()) return null

        val document = Jsoup.parseBodyFragment(rawDescription)
        document.select("br").append("\\n")
        val text = document.text().replace("\\n", "\n")
        return text.ifBlank { null }
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        statusText.contains("đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        statusText.contains("đang cập nhật", ignoreCase = true) -> SManga.ONGOING
        statusText.contains("hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun thumbnailUrl(rawThumbnail: String?): String? {
        if (rawThumbnail.isNullOrBlank()) return null
        if (rawThumbnail.startsWith("http://") || rawThumbnail.startsWith("https://")) return rawThumbnail

        val filename = rawThumbnail.removePrefix("/")
        return "$apiUrl/thumbnails/$filename"
    }

    private fun parseGenres(genres: JsonElement?): List<String> {
        if (genres == null) return emptyList()

        return when (genres) {
            is JsonArray -> genres.mapNotNull {
                (it as? JsonPrimitive)?.content?.takeIf { content -> content.isNotBlank() && content != "null" }
            }
            is JsonPrimitive -> parseGenreString(genres.content)
            else -> emptyList()
        }
    }

    private fun parseGenreString(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()

        val parsedArray = runCatching { raw.parseAs<List<String>>() }.getOrNull()
        if (parsedArray != null) {
            return parsedArray.filter { it.isNotBlank() }
        }

        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val slug = extractMangaSlug(manga.url)
            ?: throw Exception("Không tìm thấy truyện")

        val url = "$apiUrl/comic/$slug".toHttpUrl().newBuilder().build()
        return GET(url, authHeaders())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val comic = response.parseAs<ComicDto>()
        val mangaSlug = comic.slug
            ?: extractMangaSlug(response.request.url.toString())
            ?: return emptyList()
        val unlockedChapterKeys = fetchUnlockedChapterKeys(mangaSlug)

        return comic.chapters.mapNotNull { chapter ->
            val chapterSlug = chapter.slug ?: return@mapNotNull null
            val chapterName = chapter.title
                ?: chapter.name
                ?: chapter.chapterNumber?.let { "Chapter $it" }
                ?: return@mapNotNull null
            val chapterIdKey = chapter.id?.toString()
            val isUnlocked = unlockedChapterKeys.contains(chapterSlug) ||
                (chapterIdKey != null && unlockedChapterKeys.contains(chapterIdKey))

            val isLocked = chapter.isFree == false && !isUnlocked
            val chapterUrl = buildString {
                append(MANGA_PATH_PREFIX)
                append(mangaSlug)
                append("/")
                append(chapterSlug)
                if (isLocked) {
                    append("?$LOCKED_QUERY=1")
                }
            }

            SChapter.create().apply {
                name = if (isLocked) "🔒 $chapterName" else chapterName
                setUrlWithoutDomain(chapterUrl)
                date_upload = parseChapterDate(chapter.createdAt)
            }
        }
    }

    private fun fetchUnlockedChapterKeys(mangaSlug: String): Set<String> {
        val userId = authUserId ?: return emptySet()
        val url = "$apiUrl/api/comic/unlocked-chapters".toHttpUrl().newBuilder()
            .addQueryParameter("user_id", userId)
            .addQueryParameter("comic_id", mangaSlug)
            .build()

        return runCatching {
            client.newCall(GET(url, authHeaders())).execute().use { unlockedResponse ->
                if (!unlockedResponse.isSuccessful) return emptySet()
                val payload = unlockedResponse.parseAs<JsonObject>()
                val chapters = payload["chapters"] as? JsonArray ?: return emptySet()
                chapters.mapNotNull { chapter ->
                    (chapter as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                }.toSet()
            }
        }.getOrDefault(emptySet())
    }

    private fun parseChapterDate(dateText: String?): Long {
        if (dateText.isNullOrBlank()) return 0L
        return CHAPTER_DATE_FORMAT.tryParse(dateText)
    }

    // ============================== Pages =================================

    private fun extractChapterInfo(url: String): Pair<String, String>? {
        val match = CHAPTER_PATH_REGEX.find(url) ?: return null
        val mangaSlug = match.groupValues.getOrNull(1)
        val chapterSlug = match.groupValues.getOrNull(2)
        if (mangaSlug.isNullOrBlank() || chapterSlug.isNullOrBlank()) return null
        return mangaSlug to chapterSlug
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterInfo = extractChapterInfo(chapter.url)
            ?: throw Exception("Không tìm thấy chương hiện tại")

        val url = "$apiUrl/comic/${chapterInfo.first}/${chapterInfo.second}".toHttpUrl().newBuilder()
            .addQueryParameter("user_id", "undefined")
            .build()

        return GET(url, authHeaders())
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val chapterData = runCatching { body.parseAs<ChapterPageResponse>() }.getOrNull()
            ?: throw Exception("Không tìm thấy dữ liệu ảnh chương")

        val imageUrls = chapterData.images
            .map(::toAbsoluteImageUrl)
            .ifEmpty {
                val document = Jsoup.parse(body, response.request.url.toString())
                document.select("#view-chapter img, .chapter-content img, .reading-content img, .content-chapter img")
                    .map { element ->
                        element.absUrl("src").ifEmpty { element.absUrl("data-src") }
                    }
                    .filter { it.isNotBlank() }
            }

        if (imageUrls.isEmpty() && chapterData.isFree == false) {
            throw Exception(LOCKED_CHAPTER_MESSAGE)
        }

        if (imageUrls.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun toAbsoluteImageUrl(url: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        else -> "$apiUrl${if (url.startsWith('/')) url else "/$url"}"
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    private fun parseMangaPage(response: Response): MangasPage {
        val payload = response.parseAs<ComicsResponse>()

        val mangas = payload.data.mapNotNull { comic ->
            val slug = comic.slug ?: return@mapNotNull null
            val mangaTitle = comic.name ?: return@mapNotNull null

            SManga.create().apply {
                title = mangaTitle
                setUrlWithoutDomain("$MANGA_PATH_PREFIX$slug")
                thumbnail_url = thumbnailUrl(comic.thumbnail ?: comic.hotThumbnail)
            }
        }

        val currentPage = payload.pagination?.currentPage ?: 1
        val totalPages = payload.pagination?.totalPages ?: 1

        return MangasPage(mangas, currentPage < totalPages)
    }

    private fun authHeaders(): Headers = headersBuilder().apply {
        token?.let { set("Authorization", "Bearer $it") }
    }.build()

    private var cachedToken: String? = null

    @get:SuppressLint("SetJavaScriptEnabled")
    @get:Synchronized
    private val token: String?
        get() {
            cachedToken?.also { return it }
            val handler = Handler(Looper.getMainLooper())
            val latch = CountDownLatch(1)

            handler.post {
                val webView = WebView(Injekt.get<Application>())
                with(webView.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    blockNetworkImage = true
                }
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view!!.evaluateJavascript("window.localStorage.getItem('token')") { token ->
                            cachedToken = token
                                .takeUnless { it == "null" }
                                ?.removeSurrounding("\"")
                                ?.ifBlank { null }
                            latch.countDown()
                            webView.destroy()
                        }
                    }
                }
                webView.loadDataWithBaseURL(baseUrl, " ", "text/html", "UTF-8", null)
            }

            latch.await(10, TimeUnit.SECONDS)
            return cachedToken
        }

    private val authUserId: String?
        get() = token?.let(::extractUserIdFromJwt)

    private fun extractUserIdFromJwt(token: String): String? {
        val payload = token.split(".").getOrNull(1) ?: return null
        val padding = "=".repeat((4 - payload.length % 4) % 4)
        val decodedPayload = runCatching {
            String(Base64.decode(payload + padding, Base64.URL_SAFE or Base64.NO_WRAP))
        }.getOrNull() ?: return null

        val payloadObj = runCatching { decodedPayload.parseAs<JsonObject>() }.getOrNull() ?: return null
        return payloadObj["id"]?.jsonPrimitive?.contentOrNull
            ?: payloadObj["user_id"]?.jsonPrimitive?.contentOrNull
            ?: payloadObj["userId"]?.jsonPrimitive?.contentOrNull
            ?: payloadObj["sub"]?.jsonPrimitive?.contentOrNull
    }

    companion object {
        private const val PAGE_SIZE = 36
        private const val MANGA_PATH_PREFIX = "/truyen-tranh/"
        private const val LOCKED_QUERY = "is_locked"
        private const val LOCKED_CHAPTER_MESSAGE = "Vui lòng đăng nhập bằng webview vào tài khoản phù hợp để xem chương này"

        private val MANGA_SLUG_REGEX = Regex("/truyen-tranh/([^/?#]+)")
        private val CHAPTER_PATH_REGEX = Regex("/truyen-tranh/([^/?#]+)/([^/?#]+)")

        private val CHAPTER_DATE_FORMAT by lazy {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }
    }
}
