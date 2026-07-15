package eu.kanade.tachiyomi.extension.vi.yurigarden

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.lib.cryptoaes.CryptoAES
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

@Source
abstract class YuriGarden :
    HttpSource(),
    ConfigurableSource {
    private val apiUrlHost by lazy { apiUrl.toHttpUrl().host }

    override val supportsLatest = true

    private val apiUrl = baseUrl.replace("://", "://api.") + "/api"

    private val baseHost = baseUrl.toHttpUrl().host

    private val apiHost = apiUrlHost

    private val cdnUrl = baseUrl.replace("://", "://cdn.")

    private val preferences by getPreferencesLazy()

    private var cachedAuthToken: String? = null

    private var cachedMangaToken: String? = null

    private var cachedMangaTokenServerFn: String? = null

    // Strip "wv" from User-Agent so Google login works in this source.
    // Google deny login when User-Agent contains the WebView token.
    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .apply {
            build()["user-agent"]?.let { userAgent ->
                set("user-agent", removeWebViewToken(userAgent))
            }
        }

    private fun removeWebViewToken(userAgent: String): String = userAgent.replace(WEBVIEW_TOKEN_REGEX, ")")

    override val client = network.client.newBuilder()
        .addInterceptor(loginRequiredInterceptor())
        .addInterceptor(ImageDescrambler())
        .rateLimit(15, 1.minutes) { it.host == apiUrlHost }
        .build()

    private fun apiHeadersBuilder() = headersBuilder()
        .set("Referer", "$baseUrl/")
        .add("x-app-origin", "https://yurigarden.com")
        .add("x-custom-lang", "vi")
        .add("Accept", "application/json")

    private fun apiHeaders() = apiHeadersBuilder()
        .apply {
            authToken?.let { set("Authorization", "Bearer $it") }
        }
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/comics/rank/trending".toHttpUrl().newBuilder()
            .addQueryParameter("viewType", "view")
            .addQueryParameter("trendingType", "day")
            .addQueryParameter("r18", allowR18.toString())
            .build()

        return GET(url, apiHeaders())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<TrendingComic>>()

        val mangaList = result.map { comic ->
            SManga.create().apply {
                url = "/comic/${comic.id}"
                title = comic.title
                thumbnail_url = comic.image.takeIf(String::isNotBlank)?.toThumbnailUrl()
            }
        }

        val hasNextPage = false // The trending endpoint does not support pagination

        return MangasPage(mangaList, hasNextPage)
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("r18", allowR18.toString())
            .addQueryParameter("full", "true")
            .build()

        return GET(url, apiHeaders())
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<ComicsResponse>()

        val mangaList = result.comics.map { comic ->
            SManga.create().apply {
                url = "/comic/${comic.id}"
                title = comic.title
                thumbnail_url = comic.thumbnail?.toThumbnailUrl()
            }
        }

        val hasNextPage = result.totalPages > currentPage(response)

        return MangasPage(mangaList, hasNextPage)
    }

    // ============================== Search ================================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrlOrNull()
            if (url != null && url.host == baseHost) {
                val segments = url.pathSegments
                val comicIndex = segments.indexOf("comic")
                if (comicIndex != -1 && comicIndex + 1 < segments.size) {
                    val id = segments[comicIndex + 1]
                    val manga = SManga.create().apply {
                        this.url = "/comic/$id"
                        initialized = true
                    }
                    return fetchMangaDetails(manga)
                        .map {
                            it.url = manga.url
                            it.initialized = true
                            MangasPage(listOf(it), false)
                        }
                }
                throw Exception("Unsupported URL")
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/comics".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", LIMIT.toString())
            addQueryParameter("allowR18", allowR18.toString())
            addQueryParameter("full", "true")

            setQueryParameter("searchBy", "title,anotherNames")

            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }

            val filterList = filters.ifEmpty { getFilterList() }

            filterList.forEach { filter ->
                when (filter) {
                    is StatusFilter -> {
                        if (filter.slug.isNotEmpty()) {
                            addQueryParameter("status", filter.slug)
                        }
                    }
                    is SortFilter -> {
                        addQueryParameter("sort", filter.slug)
                    }
                    is GenreFilter -> {
                        val selected = filter.state
                            .filter { it.state }
                            .joinToString(",") { it.value }
                        if (selected.isNotEmpty()) {
                            addQueryParameter("genre", selected)
                        }
                    }
                    is SearchByFilter -> {
                        val selected = filter.state
                            .filter { it.state }
                            .joinToString(",") { it.value }
                        if (selected.isNotEmpty()) {
                            setQueryParameter("searchBy", selected)
                        }
                    }
                    else -> {}
                }
            }
        }.build()

        return GET(url.toString(), apiHeaders())
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // ============================== Filters ===============================

    override fun getFilterList() = getFilters()

    // ============================== Details ===============================

    private fun mangaId(manga: SManga): String = manga.url.substringAfterLast("/")

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/comics/${mangaId(manga)}", apiHeaders())

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val comic = response.parseAs<ComicDetail>()

        return SManga.create().apply {
            url = "/comic/${comic.id}"
            title = comic.title
            author = comic.authors.joinToString { it.name }
            description = comic.description
            genre = comic.genres.mapNotNull { genreMap[it] }.joinToString()
            status = when (comic.status) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = comic.thumbnail?.toThumbnailUrl()
            initialized = true
        }
    }

    // ============================== Chapters ==============================

    private fun chapterId(chapter: SChapter): String = chapter.url.substringAfterLast("/")

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/chapters/comic/${mangaId(manga)}", apiHeaders())

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.parseAs<List<ChapterData>>()

        val comicId = response.request.url.pathSegments.last()

        return chapters
            .sortedWith(
                compareByDescending<ChapterData> { it.order }
                    .thenByDescending { it.id },
            )
            .map { chapter ->
                SChapter.create().apply {
                    url = "/comic/$comicId/${chapter.id}"
                    name = buildString {
                        if (chapter.volume != null) {
                            append("Vol.${chapter.volume.toBigDecimal().stripTrailingZeros().toPlainString()} ")
                        }
                        if (chapter.order < 0) {
                            append("Oneshot")
                        } else {
                            append("Ch.${chapter.order.toBigDecimal().stripTrailingZeros().toPlainString()}")
                        }
                        if (chapter.name.isNotEmpty()) append(": ${chapter.name}")
                    }
                    date_upload = chapter.publishedAt
                    chapter_number = chapter.order.toFloat()
                    scanlator = chapter.team?.name ?: "Unknown"
                }
            }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ============================== Pages =================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/chapters/pages/${chapterId(chapter)}", apiHeaders())

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable
        .fromCallable { executePageListRequest(chapter) }
        .map(::pageListParse)

    private fun executePageListRequest(chapter: SChapter): Response {
        val request = pageListRequest(chapter)
        val response = client.newCall(request).execute()
        if (response.isSuccessful) return response

        response.close()

        if (response.code == 401) {
            throw Exception(LOGIN_REQUIRED_MESSAGE)
        }

        throw Exception("HTTP error ${response.code}")
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = decryptIfNeeded(response)

        return result.pages.mapIndexed { index, page ->
            val rawUrl = page.url.replace("_credit", "").trimStart('/')

            if (rawUrl.startsWith("comics/") || rawUrl.startsWith("teams/")) {
                val key = page.key
                val url = "$cdnUrl/storage/v1/object/public/yuri-garden-store/$rawUrl"
                    .toHttpUrl().newBuilder().apply {
                        if (!key.isNullOrEmpty()) {
                            fragment("KEY=$key")
                        }
                    }.build().toString()
                Page(index, imageUrl = url)
            } else {
                val url = rawUrl.toHttpUrlOrNull()?.toString() ?: rawUrl
                Page(index, imageUrl = url)
            }
        }
    }

    private fun decryptIfNeeded(response: Response): ChapterDetail {
        val body = response.body.string()

        return if (body.contains("\"encrypted\"")) {
            val encrypted = body.parseAs<EncryptedResponse>()
            if (encrypted.encrypted && !encrypted.data.isNullOrEmpty()) {
                decryptChapterDetail(encrypted.data)
            } else {
                body.parseAs<ChapterDetail>()
            }
        } else {
            body.parseAs<ChapterDetail>()
        }
    }

    private fun decryptChapterDetail(data: String): ChapterDetail {
        val token = getMangaToken(forceRefresh = false)
        return runCatching {
            CryptoAES.decrypt(data, token).parseAs<ChapterDetail>()
        }.getOrElse {
            cachedMangaToken = null
            val refreshedToken = getMangaToken(forceRefresh = true)
            CryptoAES.decrypt(data, refreshedToken).parseAs<ChapterDetail>()
        }
    }

    @Synchronized
    private fun getMangaToken(forceRefresh: Boolean): String {
        if (!forceRefresh) {
            cachedMangaToken?.let { return it }
        }

        val headers = headersBuilder()
            .set("Referer", "$baseUrl/")
            .set("Accept", "application/json")
            .set("x-tsr-serverFn", "true")
            .build()

        val token = client.newCall(GET("$baseUrl/_serverFn/${getMangaTokenServerFn()}", headers))
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP error ${response.code}")
                }
                extractServerFnValue(response.parseAs<ServerFnNode>(), "token")
            }
            ?: throw IOException("Không lấy được khóa giải mã chương")

        cachedMangaToken = token
        return token
    }

    @Synchronized
    private fun getMangaTokenServerFn(): String {
        cachedMangaTokenServerFn?.let { return it }

        val html = client.newCall(GET(baseUrl, headers))
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP error ${response.code}")
                }
                response.body.string()
            }

        val mainScript = MAIN_SCRIPT_REGEX.find(html)?.groupValues?.get(1)
            ?: throw IOException("Không tìm thấy bundle chính")
        val mainScriptUrl = mainScript.toHttpUrlOrNull()?.toString() ?: "$baseUrl$mainScript"
        val mainScriptBody = client.newCall(GET(mainScriptUrl, headers))
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP error ${response.code}")
                }
                response.body.string()
            }

        val routeIndex = mainScriptBody.indexOf(CHAPTER_ROUTE_PATH)
        val searchBody = if (routeIndex > 0) {
            mainScriptBody.substring(0, routeIndex).takeLast(20_000)
        } else {
            mainScriptBody
        }
        val serverFn = SERVER_FN_REGEX.findAll(searchBody)
            .lastOrNull()
            ?.groupValues
            ?.get(1)
            ?: throw IOException("Không tìm thấy khóa server function")

        cachedMangaTokenServerFn = serverFn
        return serverFn
    }

    private fun extractServerFnValue(node: ServerFnNode, key: String): String? {
        val props = node.p ?: return null
        val index = props.k.indexOf(key)
        if (index >= 0) {
            props.v.getOrNull(index)?.s?.jsonPrimitive?.contentOrNull?.let { return it }
        }

        return props.v.firstNotNullOfOrNull { extractServerFnValue(it, key) }
    }

    private fun loginRequiredInterceptor() = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val responseUrl = response.request.url
        val isApiUnauthorized = response.code == 401 && responseUrl.host == apiHost
        val isLoginPage = responseUrl.host == baseHost && responseUrl.encodedPath == "/login"

        if (isApiUnauthorized || isLoginPage) {
            cachedAuthToken = null
            response.close()
            throw IOException(LOGIN_REQUIRED_MESSAGE)
        }
        response
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Related ================================

    // disable suggested mangas on Komikku due to heavy rate limit
    override val disableRelatedMangasBySearch = true

    override fun relatedMangaListRequest(manga: SManga) = GET("$apiUrl/comics/related/${mangaId(manga)}", apiHeaders())

    override fun relatedMangaListParse(response: Response): List<SManga> {
        val result = response.parseAs<List<Comic>>()

        return result.map { comic ->
            SManga.create().apply {
                url = "/comic/${comic.id}"
                title = comic.title
                thumbnail_url = comic.thumbnail?.toThumbnailUrl()
            }
        }
    }

    // ============================== Helpers ================================

    private fun currentPage(response: Response): Int {
        val url = response.request.url
        return url.queryParameter("page")?.toIntOrNull() ?: 1
    }

    private fun String.toThumbnailUrl(): String = if (startsWith("http")) this else "$cdnUrl/storage/v1/object/public/yuri-garden-store/${trimStart('/')}"

    // ============================== Peferences ================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_R18
            title = "Hiển thị nội dung R18"
            summary = "Bật để hiển thị truyện có nội dung người lớn (18+)"
            setDefaultValue(PREF_SHOW_R18_DEFAULT)
        }.also(screen::addPreference)
    }

    private val allowR18: Boolean
        get() = preferences.getBoolean(PREF_SHOW_R18, PREF_SHOW_R18_DEFAULT)

    // ============================= Utilities ==============================

    private val authToken: String?
        @Synchronized
        get() = cachedAuthToken
            ?: getTokenFromWebView()?.also { cachedAuthToken = it }

    private fun getTokenFromWebView(): String? {
        val authData = readWebViewAuthData() ?: return null
        val firebaseToken = authData.stsTokenManager?.accessToken?.takeIf { it.isNotBlank() } ?: return null
        val email = authData.email?.takeIf { it.isNotBlank() } ?: return null
        val name = authData.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
        val avatar = authData.photoURL.orEmpty()

        val headers = apiHeadersBuilder().build()
        val body = UserAuthRequest(
            email = email,
            name = name,
            avatar = avatar,
            token = firebaseToken,
        ).toJsonRequestBody()

        return runCatching {
            client.newCall(POST("$apiUrl/users/auth", headers, body)).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.parseAs<UserAuthResponse>().accessToken.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun readWebViewAuthData(): WebViewAuthData? {
        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val bridge = WebViewAuthBridge(latch)
        val interfaceName = randomJavascriptInterfaceName()
        val script = buildWebViewAuthScript(interfaceName)
        var webView: WebView? = null

        handler.post {
            webView = WebView(Injekt.get<Application>()).apply {
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    blockNetworkImage = true
                    userAgentString = removeWebViewToken(userAgentString)
                }
                addJavascriptInterface(bridge, interfaceName)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(script, null)
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

        return bridge.payload
            ?.takeUnless { it == "null" }
            ?.ifBlank { null }
            ?.let { runCatching { it.parseAs<WebViewAuthData>() }.getOrNull() }
    }

    private fun randomJavascriptInterfaceName(): String {
        val pool = ('a'..'z') + ('A'..'Z')
        return (1..(10..20).random())
            .map { pool.random() }
            .joinToString("")
    }

    private class WebViewAuthBridge(
        private val latch: CountDownLatch,
    ) {
        @Volatile
        var payload: String? = null

        @JavascriptInterface
        fun onAuthData(value: String?) {
            payload = value
            latch.countDown()
        }
    }

    companion object {
        private const val LIMIT = 15
        private const val CHAPTER_ROUTE_PATH = "/comic/\$comicId/\$chapterId/"
        private const val LOGIN_REQUIRED_MESSAGE = "Nguồn này cần đăng nhập bằng webview để xem"
        private const val PREF_SHOW_R18 = "pref_show_r18"
        private const val PREF_SHOW_R18_DEFAULT = false

        private val MAIN_SCRIPT_REGEX = Regex("""(?:src|href)="([^"]*/assets/main-[^"]+\.js)"""")
        private val SERVER_FN_REGEX = Regex(
            """(?:const|let|var)\s+[A-Za-z_$][\w$]*\s*=\s*[A-Za-z_$][\w$]*\(\{method:"GET"\}\)\.handler\([A-Za-z_$][\w$]*\("([A-Za-z0-9]+)"\)\)""",
        )
        private val WEBVIEW_TOKEN_REGEX = Regex(""";\s*wv\)""")

        private fun buildWebViewAuthScript(interfaceName: String) = """
            (() => {
              const done = (value) => {
                window.$interfaceName.onAuthData(value ? JSON.stringify(value) : "");
              };
              try {
                const request = indexedDB.open("firebaseLocalStorageDb");
                request.onerror = () => done(null);
                request.onsuccess = () => {
                  const db = request.result;
                  if (!db.objectStoreNames.contains("firebaseLocalStorage")) {
                    db.close();
                    done(null);
                    return;
                  }
                  const transaction = db.transaction("firebaseLocalStorage", "readonly");
                  const store = transaction.objectStore("firebaseLocalStorage");
                  const getAll = store.getAll();
                  getAll.onerror = () => {
                    db.close();
                    done(null);
                  };
                  getAll.onsuccess = () => {
                    const rows = getAll.result || [];
                    const row = rows.find((item) => {
                      const value = item && item.value;
                      return value && value.stsTokenManager && value.stsTokenManager.accessToken;
                    });
                    db.close();
                    done(row ? row.value : null);
                  };
                };
              } catch (_err) {
                done(null);
              }
            })();
        """.trimIndent()
    }
}
