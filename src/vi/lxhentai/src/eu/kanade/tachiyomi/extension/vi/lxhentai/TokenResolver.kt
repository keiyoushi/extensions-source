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

    private const val TIMEOUT_SECONDS = 15L
    private const val MAX_ATTEMPTS = 3
    private const val INITIAL_POLL_DELAY_MS = 1_000L
    private const val PUZZLE_STABLE_MS = 1_500
    private const val PUZZLE_FALLBACK_MS = 5_000
    private const val POLL_INTERVAL_MS = 500L
    private const val CACHE_TTL_MS = 60_000L
    private const val WEBVIEW_WIDTH = 1080
    private const val WEBVIEW_HEIGHT = 1920
    private val WEBVIEW_TOKEN_REGEX = Regex("""\;\s*wv\)""")

    private val cache = ConcurrentHashMap<String, Pair<Result, Long>>()

    // evaluateJavascript returns the JSON representation of the returned JS value.
    // An object literal becomes `{"token":"...","srcs":[...]}` and an empty string
    // becomes `""`, which the poll loop treats as "not ready yet".
    //
    // The site renders real content images inside puzzle-container divs (canvas tiles)
    // after fetching them via fetch(). Ad/watermark images fail CORS (the CDN only
    // returns Access-Control-Allow-Origin for real content), so they never get a
    // puzzle-container. We use this to filter out ads — the same way the browser does.
    //
    // Flow: wait for actionToken → wait for puzzle-containers to stabilise → return
    // only the URLs whose containers have a puzzle child. If no puzzles appear within
    // PUZZLE_FALLBACK_MS after the token is seen, fall back to returning all URLs.
    private val TOKEN_EXTRACTION_JS = """
        (function(){
            try {
                var t = (typeof window.actionToken !== 'undefined' && window.actionToken) ? String(window.actionToken) : '';
                var s = (Array.isArray(window.__imgSrcs)) ? window.__imgSrcs : [];
                if (!t || s.length === 0) return '';

                var containers = document.querySelectorAll('[id=image-container][data-idx]');
                var puzzleCount = 0;
                var filtered = [];
                for (var i = 0; i < containers.length; i++) {
                    var c = containers[i];
                    var idx = parseInt(c.getAttribute('data-idx'), 10);
                    if (c.querySelector('[id^="puzzle-container"]') && s[idx] && s[idx].length > 0) {
                        puzzleCount++;
                        filtered.push(s[idx]);
                    }
                }

                if (puzzleCount > 0) {
                    if (puzzleCount >= containers.length) {
                        return {token: t, srcs: filtered};
                    }
                    var prev = window.__prevPuzzleCount || 0;
                    if (puzzleCount !== prev) {
                        window.__prevPuzzleCount = puzzleCount;
                        window.__puzzleStableTime = Date.now();
                        return '';
                    }
                    if (Date.now() - (window.__puzzleStableTime || 0) < $PUZZLE_STABLE_MS) return '';
                    return {token: t, srcs: filtered};
                }

                if (!window.__tokenSeenAt) window.__tokenSeenAt = Date.now();
                if (Date.now() - window.__tokenSeenAt > $PUZZLE_FALLBACK_MS) {
                    var all = s.filter(function(x){return typeof x === 'string' && x.length > 0;});
                    if (all.length > 0) return {token: t, srcs: all};
                }
                return '';
            } catch(e) {}
            return '';
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

            wv.settings.userAgentString = wv.settings.userAgentString
                .replace(WEBVIEW_TOKEN_REGEX, ")")

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

        return finalResult
    }
}
