package eu.kanade.tachiyomi.extension.vi.lxhentai

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
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

/** Loads chapter in WebView, intercepts CDN image requests to extract URLs + token. */
object TokenResolver {

    class Result(val token: String = "", val srcs: List<String> = emptyList())

    private const val TIMEOUT_SECONDS = 20L
    private const val MAX_ATTEMPTS = 3
    private const val CACHE_TTL_MS = 60_000L
    private const val WEBVIEW_WIDTH = 1080
    private const val WEBVIEW_HEIGHT = 1920
    private val WEBVIEW_TOKEN_REGEX = Regex("""\;\s*wv\)""")
    private val IMAGE_CDN_REGEX = Regex("""lxmanga\.(xyz|space)/""")

    private val cache = ConcurrentHashMap<String, Pair<Result, Long>>()

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
        val active = AtomicBoolean(true)
        val started = Semaphore(0)
        val startupError = AtomicReference<Throwable?>()

        var webView: WebView? = null
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

                view.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        if (!active.get()) return null
                        val url = request.url?.toString() ?: return null

                        if (IMAGE_CDN_REGEX.containsMatchIn(url) && payloadResult.payload == null) {
                            val token = request.requestHeaders?.get("Token")
                            if (!token.isNullOrEmpty()) {
                                payloadResult.collectImage(url, token)
                            }
                        }

                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (url != null) lastUrl = url
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        if (url != null) lastUrl = url
                        payloadResult.pageFinished()
                    }
                }

                view.loadUrl(chapterUrl)
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
            startupError.get()?.let { err ->
                throw IOException("Failed to start WebView (url=$lastUrl)", err)
            }
            payloadResult.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            active.set(false)
            handler.post {
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
        private val handler = Handler(Looper.getMainLooper())

        @Volatile
        var payload: Result? = null
            private set

        private val imageUrls = mutableListOf<String>()
        private var latestToken = ""

        @Synchronized
        fun collectImage(url: String, token: String) {
            if (payload != null) return
            if (url.isNotEmpty() && url !in imageUrls) {
                imageUrls.add(url)
            }
            if (token.isNotEmpty()) {
                latestToken = token
            }
        }

        fun pageFinished() {
            pollForCompletion()
        }

        private fun pollForCompletion() {
            handler.postDelayed({
                if (tryComplete()) return@postDelayed
                pollForCompletion()
            }, 1000)
        }

        @Synchronized
        private fun tryComplete(): Boolean {
            if (payload != null) return true
            if (imageUrls.isNotEmpty() && latestToken.isNotEmpty()) {
                payload = Result(latestToken, imageUrls.toList())
                signal.release()
                return true
            }
            return false
        }

        fun await(timeout: Long, unit: TimeUnit): Boolean {
            while (payload == null) {
                if (!signal.tryAcquire(timeout, unit)) return false
            }
            return true
        }
    }
}
