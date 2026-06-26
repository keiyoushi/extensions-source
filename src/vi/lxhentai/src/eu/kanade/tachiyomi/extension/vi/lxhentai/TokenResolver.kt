package eu.kanade.tachiyomi.extension.vi.lxhentai

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.applicationContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 1. Load chapter page in WebView → Cloudflare Turnstile solved by site JS.
 * 2. Decode image URLs from the obfuscated K/P inline script in the DOM.
 * 3. Capture the `Token` header from CDN requests via `shouldInterceptRequest`.
 * 4. Wait for both URLs + token → call back.
 */
object TokenResolver {

    class Result(val token: String = "", val srcs: List<String> = emptyList())

    private const val TIMEOUT_SECONDS = 20L
    private const val MAX_ATTEMPTS = 3
    private const val SCRIPT_RETRY_INTERVAL_MS = 500L
    private const val CACHE_TTL_MS = 60_000L
    private const val WEBVIEW_WIDTH = 1080
    private const val WEBVIEW_HEIGHT = 1920
    private val WEBVIEW_TOKEN_REGEX = Regex("""\;\s*wv\)""")

    private val cache = ConcurrentHashMap<String, Pair<Result, Long>>()

    private fun buildExtractionScript(interfaceName: String): String = """
        (function(){
            try {
                var t = (typeof window.__capturedToken !== 'undefined' && window.__capturedToken)
                    ? String(window.__capturedToken) : '';

                var s = [];
                var scripts = document.querySelectorAll('script');
                for (var i = 0; i < scripts.length; i++) {
                    var text = scripts[i].textContent || '';
                    var kMatch = text.match(/var\s+K\s*=\s*"([^"]+)"/);
                    var pMatch = text.match(/var\s+P\s*=\s*"([^"]+)"/);
                    if (kMatch && pMatch) {
                        var K = kMatch[1];
                        var P = pMatch[1];
                        try {
                            var arr = JSON.parse(atob(P));
                            for (var j = 0; j < arr.length; j++) {
                                try {
                                    var raw = atob(arr[j]);
                                    var decoded = '';
                                    for (var c = 0; c < raw.length; c++) {
                                        decoded += String.fromCharCode(
                                            raw.charCodeAt(c) ^ K.charCodeAt(c % K.length)
                                        );
                                    }
                                    if (decoded.length > 0) s.push(decoded);
                                } catch(e) { s.push(''); }
                            }
                        } catch(e) {}
                        break;
                    }
                }

                if (s.length === 0 && Array.isArray(window.__imgSrcs)) {
                    s = window.__imgSrcs.filter(function(x) {
                        return typeof x === 'string' && x.length > 0;
                    });
                }

                if (s.length === 0) return;

                if (t.length === 0) {
                    if (!window.__tokenWaitStart) window.__tokenWaitStart = Date.now();
                    if (Date.now() - window.__tokenWaitStart > 15000) {
                        $interfaceName.passResult(JSON.stringify({token: t, srcs: s}));
                    }
                    return;
                }

                $interfaceName.passResult(JSON.stringify({token: t, srcs: s}));
            } catch(e) {}
        })();
    """.trimIndent()

    fun resolve(chapterUrl: String): Result {
        cache[chapterUrl]?.let { (cached, ts) ->
            if (System.currentTimeMillis() - ts < CACHE_TTL_MS) return cached
        }

        var lastError: IOException? = null
        repeat(MAX_ATTEMPTS) {
            try {
                val res = resolveOnce(chapterUrl)
                cache[chapterUrl] = res to System.currentTimeMillis()
                return res
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError!!
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveOnce(chapterUrl: String): Result {
        val handler = Handler(Looper.getMainLooper())
        val payloadResult = WebViewPayloadResult()
        val pool = ('a'..'z') + ('A'..'Z')
        val interfaceName = (1..(10..20).random())
            .map { pool.random() }
            .joinToString("")
        val script = buildExtractionScript(interfaceName)
        val active = AtomicBoolean(true)
        val started = Semaphore(0)
        val startupError = AtomicReference<Throwable?>()
        val capturedToken = AtomicReference<String>()

        var webView: WebView? = null
        var injectScript: Runnable? = null
        var lastUrl = chapterUrl

        handler.post {
            try {
                if (!active.get()) return@post

                val context = applicationContext
                val view = WebView(context)
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
                    mediaPlaybackRequiresUserGesture = false
                    userAgentString = userAgentString.replace(WEBVIEW_TOKEN_REGEX, ")")
                }

                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(view, true)
                }

                view.addJavascriptInterface(payloadResult, interfaceName)

                view.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = request.url?.toString() ?: return null

                        if (url.contains("lxmanga.xyz") && capturedToken.get().isNullOrEmpty()) {
                            val token = request.requestHeaders?.get("Token")
                            if (!token.isNullOrEmpty()) {
                                capturedToken.set(token)
                                handler.post {
                                    if (active.get()) {
                                        view.evaluateJavascript(
                                            "window.__capturedToken='$token'",
                                            null,
                                        )
                                    }
                                }
                            }
                        }

                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (url != null) lastUrl = url
                        if (active.get() && payloadResult.payload == null) {
                            runCatching { view.evaluateJavascript(script, null) }
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        if (url != null) lastUrl = url
                        if (active.get() && payloadResult.payload == null) {
                            runCatching { view.evaluateJavascript(script, null) }
                        }
                    }
                }

                val retry = object : Runnable {
                    override fun run() {
                        if (!active.get() || payloadResult.payload != null) return

                        val netToken = capturedToken.get()
                        if (!netToken.isNullOrEmpty()) {
                            runCatching {
                                view.evaluateJavascript(
                                    "if(!window.__capturedToken)window.__capturedToken='$netToken'",
                                    null,
                                )
                            }
                        }

                        runCatching { view.evaluateJavascript(script, null) }
                        if (active.get() && payloadResult.payload == null) {
                            handler.postDelayed(this, SCRIPT_RETRY_INTERVAL_MS)
                        }
                    }
                }
                injectScript = retry

                view.loadUrl(chapterUrl)
                handler.post(retry)
            } catch (error: Throwable) {
                startupError.set(error)
            } finally {
                started.release()
            }
        }

        val completed = try {
            if (!started.tryAcquire(5, TimeUnit.SECONDS)) {
                throw IOException("Timed out starting WebView (url=$lastUrl)")
            }
            startupError.get()?.let {
                throw IOException("Failed to start WebView (url=$lastUrl)", it)
            }
            payloadResult.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            active.set(false)
            handler.post {
                injectScript?.let(handler::removeCallbacks)
                val view = webView
                webView = null
                runCatching { view?.stopLoading() }
                runCatching { view?.destroy() }
            }
        }

        if (!completed) {
            throw IOException("Không tìm thấy dữ liệu ảnh (url=$lastUrl)")
        }
        return payloadResult.payload ?: throw IOException("Failed to capture WebView payload")
    }

    private class WebViewPayloadResult {
        private val signal = Semaphore(0)

        @Volatile
        var payload: Result? = null
            private set

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passResult(data: String) {
            if (payload == null) {
                runCatching {
                    val json = org.json.JSONObject(data)
                    val token = json.optString("token", "")
                    val srcsArray = json.getJSONArray("srcs")
                    val srcs = (0 until srcsArray.length()).map { srcsArray.getString(it) }
                    payload = Result(token, srcs)
                }
                signal.release()
            }
        }

        fun await(timeout: Long, unit: TimeUnit): Boolean {
            while (payload == null) {
                if (!signal.tryAcquire(timeout, unit)) return false
            }
            return true
        }
    }
}
