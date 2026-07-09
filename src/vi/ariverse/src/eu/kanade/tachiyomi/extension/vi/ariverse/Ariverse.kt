package eu.kanade.tachiyomi.extension.vi.ariverse

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
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

@Source
abstract class Ariverse :
    HttpSource(),
    ConfigurableSource {

    private val apiUrl get() = baseUrl.replace("https://", "https://be.") + "/api/v1"

    private val imageUrl get() = baseUrl.replace("https://", "https://img.")

    override val supportsLatest = true

    private val preferences: SharedPreferences = getPreferences()

    private var cachedAuthToken: String? = null

    private val webViewUserAgent: String? by lazy {
        runCatching { WebSettings.getDefaultUserAgent(Injekt.get<Application>()) }
            .getOrNull()
            ?.let { it.replace(WEBVIEW_TOKEN_REGEX, ")") }
            ?.takeIf { it.isNotBlank() }
    }

    override val client = network.client.newBuilder()
        .addInterceptor(authInterceptor())
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "application/json")
        .apply { webViewUserAgent?.let { set("User-Agent", it) } }

    private fun apiHeaders() = headersBuilder().apply {
        authToken?.let { set("Authorization", "Bearer $it") }
    }.build()

    private val allowR18 get() = preferences.getBoolean("pref_r18", false)

    // ============================== Auth ====================================

    private val authToken: String?
        @Synchronized
        get() = cachedAuthToken
            ?: getTokenFromWebView()?.also { cachedAuthToken = it }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun getTokenFromWebView(): String? {
        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val interfaceName = randomJavascriptInterfaceName()
        val bridge = TokenBridge(latch)
        var webView: WebView? = null

        handler.post {
            webView = WebView(Injekt.get<Application>()).apply {
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    blockNetworkImage = true
                    webViewUserAgent?.let { userAgentString = it }
                }
                addJavascriptInterface(bridge, interfaceName)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(
                            """
                            (function() {
                                try {
                                    var token = localStorage.getItem('token');
                                    window['$interfaceName'].onToken(token || '');
                                } catch(e) {
                                    window['$interfaceName'].onToken('');
                                }
                            })();
                            """.trimIndent(),
                            null,
                        )
                    }
                }
                loadDataWithBaseURL(baseUrl, " ", "text/html", "UTF-8", null)
            }
        }

        latch.await(10, TimeUnit.SECONDS)

        handler.post {
            webView?.removeJavascriptInterface(interfaceName)
            webView?.destroy()
        }

        return bridge.token?.takeIf { it.isNotBlank() }
    }

    private class TokenBridge(private val latch: CountDownLatch) {
        @Volatile
        var token: String? = null

        @JavascriptInterface
        fun onToken(value: String?) {
            token = value
            latch.countDown()
        }
    }

    private fun authInterceptor() = Interceptor { chain ->
        val original = chain.request()
        val token = authToken

        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        chain.proceed(request)
    }

    private fun randomJavascriptInterfaceName(): String {
        val pool = ('a'..'z') + ('A'..'Z')
        return (1..(10..20).random())
            .map { pool.random() }
            .joinToString("")
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/stories/hot".toHttpUrl().newBuilder()
            .addQueryParameter("type", "comic")
            .addQueryParameter("is_18_plus", if (allowR18) "1" else "0")
            .addQueryParameter("limit", "50")
            .addQueryParameter("period", "week")
            .build()
        return GET(url, apiHeaders())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<HotStoryListResponse>()

        val mangas = result.data.map { story ->
            SManga.create().apply {
                url = "/comic/story/${story.slug}"
                title = story.title
                thumbnail_url = story.coverPath?.let { resolveCoverUrl(it) }
            }
        }

        return MangasPage(mangas, false)
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/stories".toHttpUrl().newBuilder()
            .addQueryParameter("type", "comic")
            .addQueryParameter("is_18_plus", if (allowR18) "1" else "0")
            .addQueryParameter("sort", "updated_at")
            .addQueryParameter("order", "desc")
            .addQueryParameter("per_page", "50")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, apiHeaders())
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/stories".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", "comic")
            addQueryParameter("is_18_plus", if (allowR18) "1" else "0")
            addQueryParameter("per_page", "50")
            addQueryParameter("page", page.toString())

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            val filterList = filters.ifEmpty { getFilterList() }

            filterList.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        val selected = filter.selectedValues()
                        if (selected.isNotEmpty()) {
                            addQueryParameter("genre", selected.joinToString(","))
                        }
                    }
                    is StatusFilter -> {
                        filter.toUriPart()?.let { addQueryParameter("status", it) }
                    }
                    is SortFilter -> {
                        filter.toSortValue()?.let { addQueryParameter("sort", it) }
                        filter.toOrderValue()?.let { addQueryParameter("order", it) }
                    }
                    else -> {}
                }
            }
        }.build()

        return GET(url, apiHeaders())
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    private fun parseMangaPage(response: Response): MangasPage {
        val result = response.parseAs<StoryListResponse>()

        val mangas = result.data.map { story ->
            SManga.create().apply {
                url = "/comic/story/${story.slug}"
                title = story.title
                thumbnail_url = story.coverPath?.let { resolveCoverUrl(it) }
            }
        }

        val hasNextPage = result.currentPage < result.lastPage

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Details ===============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        val url = "$apiUrl/stories/$slug".toHttpUrl()
        return GET(url, apiHeaders())
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val story = response.parseAs<StoryDetailResponse>().data

        return SManga.create().apply {
            url = "/comic/story/${story.slug}"
            title = story.title
            thumbnail_url = story.coverPath?.let { resolveCoverUrl(it) }
            author = story.author
            artist = story.artist
            description = story.description?.let { parseDescription(it) }
            genre = story.genres?.joinToString { it.name }
            status = parseStatus(story.status)
        }
    }

    private fun resolveCoverUrl(coverPath: String): String {
        if (coverPath.startsWith("http://") || coverPath.startsWith("https://")) {
            return coverPath
        }
        return "$imageUrl/${coverPath.replace("\\", "/")}"
    }

    private fun parseDescription(html: String): String {
        val normalized = html
            .replace(BR_TAG_REGEX, "\n")
            .replace("&nbsp;", " ")

        return Jsoup.parse(normalized).wholeText()
            .replace(HORIZONTAL_SPACE_REGEX, " ")
            .replace(MULTI_NEWLINE_REGEX, "\n")
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
        status.contains("completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        val url = "$apiUrl/stories/$slug/chapters".toHttpUrl()
        return GET(url, apiHeaders())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterData = response.parseAs<ChapterListResponse>().data

        return chapterData.chapters
            .sortedByDescending { it.number }
            .map { chapter ->
                SChapter.create().apply {
                    url = "/comic/story/${chapterData.story.slug}/${chapter.slug}"
                    name = chapter.title
                    chapter_number = chapter.number.toFloat()
                    date_upload = chapter.publishedAt?.let { DATE_FORMAT.tryParse(it) } ?: 0L
                }
            }
    }

    // ============================== Pages =================================

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = chapter.url.trim('/').split("/")
        val storySlug = parts.getOrElse(2) { "" }
        val chapterSlug = parts.getOrElse(3) { "" }
        val url = "$apiUrl/stories/$storySlug/chapters/$chapterSlug".toHttpUrl()
        return GET(url, apiHeaders())
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterDetail = response.parseAs<ChapterDetailResponse>().data

        if (chapterDetail.contentLocked) {
            throw Exception(LOGIN_WEBVIEW_MESSAGE)
        }

        val content = chapterDetail.content.orEmpty()

        if (content.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return content.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = "pref_r18"
            title = "Hiển thị nội dung 18+"
            summary = "Cần đăng nhập bằng webiew để sử dụng tính năng này."
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    companion object {
        private val BR_TAG_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
        private val HORIZONTAL_SPACE_REGEX = Regex("[\\t\\x0B\\f\\r ]+")
        private val MULTI_NEWLINE_REGEX = Regex("\\n{2,}")

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }

        private val WEBVIEW_TOKEN_REGEX = Regex(""";\s*wv\)""")

        private const val LOGIN_WEBVIEW_MESSAGE = "Vui lòng đăng nhập vào tài khoản phù hợp qua Webview để đọc chương này"
    }
}
