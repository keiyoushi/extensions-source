package eu.kanade.tachiyomi.extension.vi.hentaicube

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import keiyoushi.utils.applicationContext
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class HentaiCB :
    Madara(
        "CBHentai",
        "https://2tencb.pro",
        "vi",
        SimpleDateFormat("dd/MM/yyyy", Locale("vi")),
    ),
    ConfigurableSource {

    override val id: Long = 823638192569572166

    override val client: OkHttpClient = network.client.newBuilder()
        .followRedirects(false)
        .addInterceptor { chain ->
            val maxRedirects = 5
            var request = chain.request()
            var response = chain.proceed(request)
            var redirectCount = 0

            while (response.isRedirect && redirectCount < maxRedirects) {
                val newUrl = response.header("Location") ?: break
                val newUrlHttp = newUrl.toHttpUrl()
                val redirectedDomain = newUrlHttp.run { "$scheme://$host" }
                if (redirectedDomain != baseUrl) {
                    synchronized(prefsLock) {
                        preferences.edit().putString(BASE_URL_PREF, redirectedDomain).commit()
                    }
                }
                response.close()
                request = request.newBuilder()
                    .url(newUrlHttp)
                    .build()
                response = chain.proceed(request)
                redirectCount++
            }
            if (redirectCount >= maxRedirects) {
                response.close()
                throw java.io.IOException("Too many redirects: $maxRedirects")
            }
            response
        }
        .rateLimit(3)
        .build()

    private val preferences: SharedPreferences = getPreferences()
    private val prefsLock = Any()

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != super.baseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, super.baseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, super.baseUrl)
                    .apply()
            }
        }
    }
    private fun getPrefBaseUrl(): String = synchronized(prefsLock) {
        preferences.getString(BASE_URL_PREF, super.baseUrl)!!
    }

    override val baseUrl: String
        get() = getPrefBaseUrl().ifBlank { super.baseUrl }

    override val filterNonMangaItems = false

    override val mangaSubString = "read"

    override val altNameSelector = ".post-content_item:contains(Tên khác) .summary-content"

    private val thumbnailOriginalUrlRegex = Regex("-\\d+x\\d+(\\.[a-zA-Z]+)$")

    override fun popularMangaFromElement(element: Element): SManga = super.popularMangaFromElement(element).apply {
        val img = element.selectFirst("img")
        thumbnail_url = imageFromElement(img!!)?.replace(thumbnailOriginalUrlRegex, "$1")
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val mangaUrl = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment(mangaSubString)
                addPathSegment(query.substringAfter(URL_SEARCH_PREFIX))
                addPathSegment("")
            }.build()
            return client.newCall(GET(mangaUrl, headers))
                .asObservableSuccess().map { response ->
                    val manga = mangaDetailsParse(response).apply {
                        setUrlWithoutDomain(mangaUrl.toString())
                        initialized = true
                    }

                    MangasPage(listOf(manga), false)
                }
        }

        // Special characters causing search to fail
        val queryFixed = query
            .replace("–", "-")
            .replace("’", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("…", "...")

        return super.fetchSearchManga(page, queryFixed, filters)
    }

    private val oldMangaUrlRegex = Regex("^$baseUrl/\\w+/")

    // Change old entries from mangaSubString
    override fun getMangaUrl(manga: SManga): String = super.getMangaUrl(manga)
        .replace(oldMangaUrlRegex, "$baseUrl/$mangaSubString/")

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chaptersWrapper = document.select("div[id^=manga-chapters-holder]")

        var chapterElements = document.select(chapterListSelector())

        if (chapterElements.isEmpty() && !chaptersWrapper.isNullOrEmpty()) {
            val mangaUrl = document.location().removeSuffix("/")
            val mangaId = chaptersWrapper.attr("data-id")

            val allChapters = Elements()
            var page = 1

            while (true) {
                val xhrRequest = xhrChaptersRequest(mangaUrl, page)
                var xhrResponse = client.newCall(xhrRequest).execute()

                // Newer Madara versions throws HTTP 400 when using the old endpoint.
                if (xhrResponse.code == 400 && page == 1) {
                    xhrResponse.close()
                    val oldRequest = oldXhrChaptersRequest(mangaId)
                    xhrResponse = client.newCall(oldRequest).execute()
                }

                val xhrDocument = xhrResponse.asJsoup()
                allChapters.addAll(xhrDocument.select(chapterListSelector()))

                val hasNextPage = xhrDocument.selectFirst("div.pagination a[data-page='${page + 1}']") != null
                xhrResponse.close()

                if (!hasNextPage) {
                    break
                }
                page++
            }
            chapterElements = allChapters
        }

        return chapterElements.map(::chapterFromElement)
    }

    private fun xhrChaptersRequest(mangaUrl: String, page: Int): Request {
        val request = xhrChaptersRequest(mangaUrl)
        if (page <= 1) return request

        val url = request.url.newBuilder()
            .addQueryParameter("t", page.toString())
            .build()

        return request.newBuilder().url(url).build()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val request = pageListRequest(chapter)
        val url = request.url.toString()
        runInWebView(url)
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    @SuppressLint("SetJavaScriptEnabled")
    @Synchronized
    private fun runInWebView(url: String): List<Page> {
        val handler = Handler(Looper.getMainLooper())
        val result = WebViewImageResult()
        val pool = ('a'..'z') + ('A'..'Z')
        val interfaceName = (1..(10..20).random())
            .map { pool.random() }
            .joinToString("")
        val active = java.util.concurrent.atomic.AtomicBoolean(true)
        val started = Semaphore(0)
        val startupError = java.util.concurrent.atomic.AtomicReference<Throwable?>()

        var webView: WebView? = null
        var lastUrl = url

        handler.post {
            try {
                if (!active.get()) return@post

                val view = WebView(applicationContext)
                webView = view

                runCatching {
                    view.layoutParams = ViewGroup.LayoutParams(WEBVIEW_WIDTH, WEBVIEW_HEIGHT)
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(WEBVIEW_WIDTH, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(WEBVIEW_HEIGHT, View.MeasureSpec.EXACTLY),
                    )
                    view.layout(0, 0, WEBVIEW_WIDTH, WEBVIEW_HEIGHT)
                }

                with(view.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    blockNetworkImage = false
                    userAgentString = headers["User-Agent"]
                }

                view.addJavascriptInterface(result, interfaceName)

                val checkImagesScript = """
                    (function() {
                        var imgs = document.querySelectorAll('#manga-secure-reader img');
                        if (imgs.length > 0) {
                            var urls = [];
                            imgs.forEach(function(img) {
                                var src = img.getAttribute('data-src') || img.getAttribute('src') || '';
                                if (src && src.indexOf('data:image') === -1) {
                                    urls.push(src);
                                }
                            });
                            if (urls.length > 0) {
                                window.$interfaceName.passImages(JSON.stringify(urls));
                                return true;
                            }
                        }
                        return false;
                    })();
                """.trimIndent()

                view.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (url != null) lastUrl = url
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        if (url != null) lastUrl = url
                        if (active.get() && result.images == null) {
                            runCatching { view.evaluateJavascript(checkImagesScript, null) }
                        }
                    }
                }

                view.loadUrl(url)

                val pollRunnable = object : Runnable {
                    override fun run() {
                        if (!active.get() || result.images != null) return
                        runCatching { view.evaluateJavascript(checkImagesScript, null) }
                        if (active.get() && result.images == null) {
                            handler.postDelayed(this, POLL_INTERVAL_MS)
                        }
                    }
                }
                handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)

                started.release()
            } catch (error: Throwable) {
                startupError.set(error)
                started.release()
            }
        }

        val completed = try {
            if (!started.tryAcquire(WEBVIEW_START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw Exception("Timed out starting WebView (url=$lastUrl)")
            }
            startupError.get()?.let {
                throw Exception("Failed to start WebView (url=$lastUrl)", it)
            }
            result.await(WEBVIEW_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            active.set(false)
            handler.post {
                val view = webView
                webView = null
                runCatching { view?.stopLoading() }
                runCatching { view?.destroy() }
            }
        }

        if (!completed || result.images.isNullOrEmpty()) {
            throw Exception("Failed to load images from WebView (url=$lastUrl)")
        }

        return result.images!!.mapIndexed { index, imageUrl ->
            Page(index, url, imageUrl)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = "$BASE_URL_PREF_SUMMARY${getPrefBaseUrl()}"
            setDefaultValue(super.baseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: ${super.baseUrl}"

            val validate = { str: String ->
                if (str.isBlank()) {
                    true
                } else {
                    runCatching { str.toHttpUrl() }.isSuccess && domainRegex.matchEntire(str) != null
                }
            }

            setOnBindEditTextListener { editText ->
                editText.addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                        override fun afterTextChanged(editable: Editable?) {
                            editable ?: return
                            val text = editable.toString()
                            val valid = validate(text)
                            editText.error = if (!valid) "https://example.com" else null
                            editText.rootView.findViewById<Button>(android.R.id.button1)?.isEnabled = editText.error == null
                        }
                    },
                )
            }

            setOnPreferenceChangeListener { _, newValue ->
                val isValid = validate(newValue as String)
                if (isValid) {
                    summary = "$BASE_URL_PREF_SUMMARY$newValue"
                }
                isValid
            }
        }.let(screen::addPreference)
    }

    private class WebViewImageResult {
        private val signal = Semaphore(0)

        @Volatile
        var images: List<String>? = null
            private set

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passImages(data: String) {
            if (images == null) {
                try {
                    val jsonArray = org.json.JSONArray(data)
                    images = (0 until jsonArray.length()).map { jsonArray.getString(it) }
                    signal.release()
                } catch (_: Exception) {}
            }
        }

        fun await(timeout: Long, unit: TimeUnit): Boolean = signal.tryAcquire(timeout, unit) && images != null
    }

    companion object {
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt.\n" +
                "Để trống để sử dụng URL mặc định.\n" +
                "Hiện tại sử dụng: "
        private val domainRegex = Regex("""^https?://(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9]{1,6}$""")
        private const val WEBVIEW_WIDTH = 1080
        private const val WEBVIEW_HEIGHT = 1920
        private const val WEBVIEW_START_TIMEOUT_SECONDS = 10L
        private const val WEBVIEW_TIMEOUT_SECONDS = 30L
        private const val POLL_INTERVAL_MS = 2000L
    }
}
