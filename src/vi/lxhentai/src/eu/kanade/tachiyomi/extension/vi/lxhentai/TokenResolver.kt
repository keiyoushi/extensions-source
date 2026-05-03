package eu.kanade.tachiyomi.extension.vi.lxhentai

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object TokenResolver {

    @Serializable
    class Result(val token: String = "", val srcs: List<String> = emptyList())

    private const val TIMEOUT_SECONDS = 45L
    private const val INITIAL_POLL_DELAY_MS = 2_000L
    private const val POLL_INTERVAL_MS = 500L
    private const val CACHE_TTL_MS = 60_000L
    private const val WEBVIEW_WIDTH = 1080
    private const val WEBVIEW_HEIGHT = 1920

    private val cache = ConcurrentHashMap<String, Pair<Result, Long>>()

    // evaluateJavascript returns the JSON representation of the returned JS value.
    // An object literal becomes `{"token":"...","srcs":[...]}` and an empty string
    // becomes `""`, which the poll loop treats as "not ready yet".
    private val TOKEN_EXTRACTION_JS = """
        (function(){
            try {
                var t = (typeof window.actionToken !== 'undefined' && window.actionToken) ? String(window.actionToken) : '';
                var s = (Array.isArray(window.__imgSrcs)) ? window.__imgSrcs.filter(function(x){return typeof x === 'string' && x.length > 0;}) : [];
                if (t && s.length > 0) {
                    return {token: t, srcs: s};
                }
            } catch(e) {}
            return '';
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    fun resolve(chapterUrl: String): Result {
        cache[chapterUrl]?.let { (cached, ts) ->
            if (System.currentTimeMillis() - ts < CACHE_TTL_MS) return cached
        }

        val context = Injekt.get<Application>()
        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        var result: Result? = null
        lateinit var poll: Runnable

        handler.post {
            val wv = WebView(context)
            webView = wv

            wv.layoutParams = ViewGroup.LayoutParams(WEBVIEW_WIDTH, WEBVIEW_HEIGHT)
            wv.measure(
                View.MeasureSpec.makeMeasureSpec(WEBVIEW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(WEBVIEW_HEIGHT, View.MeasureSpec.EXACTLY),
            )
            wv.layout(0, 0, WEBVIEW_WIDTH, WEBVIEW_HEIGHT)

            with(wv.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                blockNetworkImage = false
                mediaPlaybackRequiresUserGesture = false
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

            wv.webViewClient = WebViewClient()

            poll = Runnable {
                if (latch.count == 0L) return@Runnable
                wv.evaluateJavascript(TOKEN_EXTRACTION_JS) { raw ->
                    if (latch.count == 0L) return@evaluateJavascript
                    if (raw.isNullOrEmpty() || raw == "null" || raw == "\"\"") {
                        handler.postDelayed(poll, POLL_INTERVAL_MS)
                        return@evaluateJavascript
                    }
                    runCatching { raw.parseAs<Result>() }
                        .getOrNull()
                        ?.takeIf { it.token.isNotBlank() && it.srcs.isNotEmpty() }
                        ?.let {
                            result = it
                            latch.countDown()
                        }
                        ?: handler.postDelayed(poll, POLL_INTERVAL_MS)
                }
            }

            wv.loadUrl(chapterUrl)
            handler.postDelayed(poll, INITIAL_POLL_DELAY_MS)
        }

        val solved = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        handler.post {
            handler.removeCallbacks(poll)
            webView?.stopLoading()
            webView?.destroy()
        }

        val finalResult = result
        if (!solved || finalResult == null) {
            throw IOException("Mở chương trong WebView để giải Cloudflare.")
        }

        cache[chapterUrl] = finalResult to System.currentTimeMillis()
        return finalResult
    }
}
