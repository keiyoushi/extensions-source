package eu.kanade.tachiyomi.extension.ko.ntk

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class NTKBase(
    override val name: String,
    protected val contentKind: String, // "manhwa" or "webtoon" — used for URL path construction
) : HttpSource(),
    ConfigurableSource {

    private val json = Json { ignoreUnknownKeys = true }

    // Used for JSON API requests — sets Accept: application/json so headerCleanerInterceptor leaves it alone
    protected val apiHeaders by lazy {
        headers.newBuilder()
            .set("Accept", "application/json")
            .build()
    }

    override val lang = "ko"
    override val supportsLatest = true
    protected val preferences by lazy { getPreferences() }

    // leaving the below codeblock in case they change the naming scheme again
    // Domain number is user-configurable and auto-updated when the site redirects (e.g. ntk01 → ntk02)
//    protected val rootUrl: String
//        get() {
//            val domainNumber = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)!!
//              return "https://sbxh$domainNumber.com"
// //            return "https://ntk$domainNumber.com"
//        }
    protected val rootUrl: String
        get() {
            val stored = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)!!
            val domainNumber = stored.trimStart('0').ifEmpty { "0" }
            if (domainNumber != stored) {
                preferences.edit().putString(PREF_DOMAIN_KEY, domainNumber).apply()
            }
            return "https://sbxh$domainNumber.com"
        }

    // baseUrl is set to the content-specific path so "Open in WebView" lands on the right section
    // Subclasses can override webViewPath when the WebView path differs from contentKind (e.g. NTKWebtoon uses "ing")
    protected open val webViewPath: String get() = contentKind
    override val baseUrl: String get() = "$rootUrl/$webViewPath"

    // Detail/chapter/page requests use rootUrl directly to avoid baseUrl's content-path prefix doubling the path
    override fun mangaDetailsRequest(manga: SManga) = GET(rootUrl + manga.url, headers)
    override fun chapterListRequest(manga: SManga) = GET(rootUrl + manga.url, headers)

    // override fun pageListRequest(chapter: SChapter) = GET(rootUrl + chapter.url, headers)
    // override fun pageListRequest(chapter: SChapter) = GET(rootUrl + chapter.url, apiHeaders)

    override fun pageListRequest(chapter: SChapter) = GET(
        url = rootUrl + chapter.url,
        headers = headers.newBuilder().add("X-WebView-Intercept", "true").build(),
    )

    // --- INTERCEPTORS ---

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private val trojanWebViewInterceptor = Interceptor { chain ->
        val request = chain.request()

        if (request.header("X-WebView-Intercept") == null) {
            return@Interceptor chain.proceed(request)
        }

        var finalHtml: String? = null
        var lastSeenHtml = "No HTML captured" // Our debug camera
        val latch = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())

        handler.post {
            val context = Injekt.get<Application>()
            val webView = WebView(context)

            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true

            // Give the invisible WebView a fake physical screen size (1080p)
            webView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY),
            )
            webView.layout(0, 0, 1080, 1920)

            // 1. The Disguise: Steal the OkHttp User-Agent so we look like a real browser
            webView.settings.userAgentString = request.header("User-Agent")
                ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

            // 2. The Keys: Tell the WebView it's allowed to use our Cloudflare cookies
            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

            webView.addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun exfiltrate(html: String) {
                        finalHtml = html
                        latch.countDown()
                    }

                    @JavascriptInterface
                    fun updateDebug(html: String) {
                        lastSeenHtml = html // Constantly records what the WebView is looking at
                    }
                },
                "TrojanTunnel",
            )

            webView.webViewClient = object : WebViewClient() {
                // This runs the millisecond the page starts loading
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    // 1. Neuter the disable-devtool script
                    view.evaluateJavascript("window.__ntkDevtoolsPreflight = 1;", null)

                    // 2. The Wiretap: Intercept the browser's fetch API
                    val wiretapScript = """
                        const originalFetch = window.fetch;
                        window.fetch = async function() {
                            const response = await originalFetch.apply(this, arguments);

                            // Check if the browser is asking for the manhwa images
                            let reqUrl = arguments[0] && arguments[0].url ? arguments[0].url : arguments[0];
                            if (reqUrl && reqUrl.toString().includes('/api/manhwa-images')) {
                                // Clone the JSON payload and sneak it out the tunnel!
                                response.clone().text().then(text => {
                                    window.TrojanTunnel.exfiltrate(text);
                                });
                            }
                            return response;
                        };
                    """.trimIndent()
                    view.evaluateJavascript(wiretapScript, null)

                    super.onPageStarted(view, url, favicon)
                }

                // We no longer need to scrape the DOM, but we leave the debug camera running just in case
                override fun onPageFinished(view: WebView, url: String) {
                    val spyScript = """
                        setInterval(function() {
                            window.TrojanTunnel.updateDebug(document.documentElement.outerHTML);
                        }, 500);
                    """.trimIndent()
                    view.evaluateJavascript(spyScript, null)
                }
            }

            webView.loadUrl(request.url.toString())
        }

        latch.await(20, TimeUnit.SECONDS)

        if (finalHtml != null) {
            // Check if we stole JSON or HTML and set the correct media type
            val isJson = finalHtml!!.trim().startsWith("{")
            val mediaType = if (isJson) "application/json" else "text/html"

            return@Interceptor Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(finalHtml!!.toResponseBody(mediaType.toMediaType()))
                .build()
        } else {
            // 3. The Black Box: If we time out, print the last seen HTML to the crash log
            throw Exception("Trojan timeout! WebView is stuck. Last seen HTML: \n" + lastSeenHtml.take(1500))
        }
    }

    // Strips Next.js RSC headers that would confuse the server into returning partial JSON instead of full HTML.
    // Only adds the HTML Accept header if one isn't already set (preserves Accept: application/json on API calls).
    private val headerCleanerInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .removeHeader("rsc")
            .removeHeader("next-router-state-tree")
            .removeHeader("next-url")

        if (originalRequest.header("Accept") == null) {
            requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        }

        chain.proceed(requestBuilder.build())
    }

    // Detects if the site has migrated to a new domain number after a redirect and saves it to preferences
    private val domainUpdateInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        val finalUrl = response.request.url.toString()
        val matchResult = """sbxh(\d+)\.com""".toRegex().find(finalUrl)

        if (matchResult != null) {
            val newDomainNumber = matchResult.groupValues[1]
            val currentDomainNumber = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)
            if (newDomainNumber != currentDomainNumber) {
                preferences.edit().putString(PREF_DOMAIN_KEY, newDomainNumber).apply()
            }
        }
        response
    }

    // Applies per-image rate limiting during downloads only — has no effect while reading
    private var lastImageRequestTime = 0L
    private val smartRateLimitInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()

        val isImage = url.contains("toonflix.app") ||
            url.contains("11toon8.com") ||
            url.endsWith(".jpg") ||
            url.endsWith(".png") ||
            url.endsWith(".webp")

        val isDownload = request.header("X-Download") != null

        if (isImage && isDownload) {
            val rateLimitSeconds = preferences.getString(PREF_RATELIMIT_KEY, PREF_RATELIMIT_DEFAULT)!!.toLong()
            if (rateLimitSeconds > 0) {
                val delayMillis = rateLimitSeconds * 1000L
                synchronized(this) {
                    val now = System.currentTimeMillis()
                    val timeToWait = delayMillis - (now - lastImageRequestTime)
                    if (timeToWait > 0) Thread.sleep(timeToWait)
                    lastImageRequestTime = System.currentTimeMillis()
                }
            }
        }
        chain.proceed(request)
    }

    // Redirects bare root URL requests to the correct content section for WebView
    private val webViewRedirectInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        val isExactRoot = url == rootUrl || url == "$rootUrl/"
        if (isExactRoot) {
            chain.proceed(request.newBuilder().url("$rootUrl/$webViewPath").build())
        } else {
            chain.proceed(request)
        }
    }

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .addInterceptor(headerCleanerInterceptor)
            .addInterceptor(domainUpdateInterceptor)
            .addInterceptor(smartRateLimitInterceptor)
            .addInterceptor(webViewRedirectInterceptor)
            .addInterceptor(trojanWebViewInterceptor)
            .build()
    }

    // --- PARSE LOGIC ---

    // Parses the card grid HTML used by text search results
    protected fun htmlCardParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.card-grid > a.card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.select("p.subject").text()
                thumbnail_url = element.select("div.thumb img:not(.platform-icon)").attr("abs:src")
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    // Parses the JSON API response used by popular and filter-based search for both manga and webtoon
    override fun popularMangaParse(response: Response): MangasPage {
        val data = json.parseToJsonElement(response.body.string()).jsonObject
        val mangas = data["works"]!!.jsonArray.map {
            val work = it.jsonObject
            SManga.create().apply {
                url = "/$contentKind/${work["sourceWorkId"]!!.jsonPrimitive.content}"
                title = work["title"]!!.jsonPrimitive.content
                thumbnail_url = work["thumbnailUrl"]?.jsonPrimitive?.content
                genre = work["genre"]?.jsonPrimitive?.content
            }
        }
        return MangasPage(mangas, data["hasMore"]!!.jsonPrimitive.boolean)
    }

    // Parses the manga latest updates page by extracting all 200 entries from the embedded RSC payload.
    // The server pre-loads all entries in a Next.js RSC script tag on initial load — no pagination needed.
    // Deduplicates by sourceWorkId since the same series can appear multiple times across recent episodes.
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val rscData = document.select("script")
            .map { it.data() }
            .firstOrNull { "allCards" in it }
            ?: return MangasPage(emptyList(), false)

        // Extract JSON string content between push([1," and "])
        val rawContent = rscData
            .substringAfter("[1,\"")
            .substringBeforeLast("\"])")

        // Unescape JavaScript string encoding — \\ must come before \"
        val unescaped = rawContent
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\/", "/")

        val marker = "\"allCards\":"
        val markerIdx = unescaped.indexOf(marker)
        if (markerIdx < 0) return MangasPage(emptyList(), false)

        // Walk brackets to find the end of the allCards array
        val arrayStart = markerIdx + marker.length
        var depth = 0
        var arrayEnd = arrayStart
        for (i in arrayStart until unescaped.length) {
            when (unescaped[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        arrayEnd = i + 1
                        break
                    }
                }
            }
        }

        val cards = json.parseToJsonElement(unescaped.substring(arrayStart, arrayEnd)).jsonArray

        val seen = mutableSetOf<String>()
        val mangas = cards.mapNotNull {
            val card = it.jsonObject
            val sid = card["sourceWorkId"]!!.jsonPrimitive.content
            if (seen.add(sid)) {
                SManga.create().apply {
                    url = "/$contentKind/$sid"
                    title = card["workTitle"]!!.jsonPrimitive.content
                    thumbnail_url = card["thumbnailUrl"]?.jsonPrimitive?.content
                    genre = card["genre"]?.jsonPrimitive?.content
                    author = card["author"]?.jsonPrimitive?.content
                }
            } else {
                null
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    // Routes to JSON parser for API results, HTML card parser for text search results
    override fun searchMangaParse(response: Response): MangasPage {
        val contentType = response.header("Content-Type") ?: ""
        return if (contentType.contains("application/json")) {
            popularMangaParse(response)
        } else {
            htmlCardParse(response)
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.select("h1.hero-v2-title").text()
            author = document.select("div.hero-v2-author a").text()
            description = document.select("p.hero-v2-desc").text()
            thumbnail_url = document.select("div.hero-v2-thumb img").attr("abs:src")

            val statusText = document.select("span.pill-status").text()
            status = when {
                statusText.contains("연재중") -> SManga.ONGOING
                statusText.contains("완결") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            genre = document.select("a.hero-v2-tag").joinToString(", ") {
                it.text().replace("#", "").trim()
            }
        }
    }

    private val dateFormat = SimpleDateFormat("yy.MM.dd", Locale.KOREA)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.ep-list-v2 > li.ep-row-v2").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.select("a.ep-row-v2-link").attr("href"))
                name = element.select("div.ep-row-v2-title strong").text()
                date_upload = element.select("span.ep-row-v2-date").text()
                    .let { runCatching { dateFormat.parse(it)?.time ?: 0L }.getOrDefault(0L) }
            }
        }
    }

//    override fun pageListParse(response: Response): List<Page> {
//        val document = response.asJsoup()
//        return document.select("div.vw-imgs img").mapIndexed { i, img ->
//            Page(i, imageUrl = img.attr("abs:src"))
//        }
//    }

    override fun pageListParse(response: Response): List<Page> {
        val responseBody = response.body.string()
        val data = json.parseToJsonElement(responseBody).jsonObject
        val imagesArray = data["images"]!!.jsonArray

        return imagesArray.mapIndexed { i, element ->
            val imageUrl = element.jsonObject["src"]!!.jsonPrimitive.content
            Page(i, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used")

    // --- SETTINGS ---

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_DOMAIN_KEY
            title = "도메인 번호 (sbxh#.com)"
            summary = "현재 도메인 번호: ${preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)}\n숫자만 입력하세요 (예: 1, 2, 300)"
            setDefaultValue(PREF_DOMAIN_DEFAULT)
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_RATELIMIT_KEY
            title = "다운로드 속도 제한"
            summary = "현재 설정: ${preferences.getString(PREF_RATELIMIT_KEY, PREF_RATELIMIT_DEFAULT)}초마다 1장\n※ 다운로드할 때만 적용 (읽기 중에는 영향 없음)"
            entries = arrayOf(
                "제한 없음 (최고속)",
                "1초마다 다운로드",
                "2초마다 다운로드",
                "3초마다 다운로드",
                "4초마다 다운로드",
                "5초마다 다운로드",
                "6초마다 다운로드",
                "7초마다 다운로드",
                "8초마다 다운로드",
                "9초마다 다운로드",
            )
            entryValues = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
            setDefaultValue(PREF_RATELIMIT_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_DOMAIN_KEY = "pref_domain_key"
        private const val PREF_DOMAIN_DEFAULT = "3"
        private const val PREF_RATELIMIT_KEY = "pref_ratelimit_key"
        private const val PREF_RATELIMIT_DEFAULT = "5"

        const val PAGE_SIZE = 49
    }
}
