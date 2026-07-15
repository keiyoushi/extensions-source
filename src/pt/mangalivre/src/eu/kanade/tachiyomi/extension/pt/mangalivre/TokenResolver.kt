package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.applicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Loads a real chapter reader page in a hidden WebView and observes the headers the site's own JS
 * sends, instead of decoding the rotating gate header out of the bundle. Returns every candidate
 * seen (the site also emits decoys) for the caller to validate against the real endpoint.
 */
object TokenResolver {

    data class ClientToken(val header: String, val value: String)

    private const val TIMEOUT_SECONDS = 25L
    private const val SETTLE_MS = 2_000L
    private const val WEBVIEW_WIDTH = 1080
    private const val WEBVIEW_HEIGHT = 1920
    private const val MAX_TOKEN_VALUE_LEN = 256

    private val STANDARD_HEADERS = setOf(
        "accept", "accept-language", "accept-encoding", "content-type", "content-length",
        "authorization", "user-agent", "referer", "origin", "cookie", "cache-control",
        "pragma", "connection", "host", "dnt", "x-csrf-token", "x-requested-with", "priority",
        "sec-fetch-dest", "sec-fetch-mode", "sec-fetch-site", "sec-ch-ua", "sec-ch-ua-mobile",
        "sec-ch-ua-platform",
    )

    private val CAPTURE_SCRIPT = """
        (function() {
            const std = new Set([${STANDARD_HEADERS.joinToString(",") { "'$it'" }}]);
            const report = function(k, v) {
                if (k && v && !std.has(String(k).toLowerCase()) && String(v).length < $MAX_TOKEN_VALUE_LEN) {
                    TokenBridge.onToken(k, v);
                }
            };
            const reportHeaders = function(h) {
                if (!h) return;
                const entries = h instanceof Headers ? Array.from(h.entries()) : Object.entries(h);
                entries.forEach(function(e) { report(e[0], e[1]); });
            };
            const origFetch = window.fetch;
            window.fetch = function(input, init) {
                if (init && init.headers) reportHeaders(init.headers);
                if (input && typeof input === 'object' && input.headers) reportHeaders(input.headers);
                return origFetch.apply(this, arguments);
            };
            const origSetHeader = XMLHttpRequest.prototype.setRequestHeader;
            XMLHttpRequest.prototype.setRequestHeader = function(k, v) {
                report(k, v);
                return origSetHeader.apply(this, arguments);
            };
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    fun resolve(pageUrl: String, userAgent: String?): List<ClientToken> {
        val handler = Handler(Looper.getMainLooper())
        val pageFinishedLatch = CountDownLatch(1)
        val candidates = mutableListOf<ClientToken>()
        var webView: WebView? = null

        handler.post {
            try {
                val view = WebView(applicationContext)
                webView = view

                view.layoutParams = ViewGroup.LayoutParams(WEBVIEW_WIDTH, WEBVIEW_HEIGHT)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(WEBVIEW_WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(WEBVIEW_HEIGHT, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, WEBVIEW_WIDTH, WEBVIEW_HEIGHT)

                with(view.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    if (!userAgent.isNullOrBlank()) userAgentString = userAgent
                }

                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(view, true)
                }

                view.addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onToken(header: String, value: String) {
                            val token = ClientToken(header, value)
                            synchronized(candidates) {
                                if (token !in candidates) candidates.add(token)
                            }
                        }
                    },
                    "TokenBridge",
                )

                view.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        view.evaluateJavascript(CAPTURE_SCRIPT, null)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(CAPTURE_SCRIPT, null)
                        pageFinishedLatch.countDown()
                    }
                }

                view.loadUrl(pageUrl)
            } catch (_: Throwable) {
                pageFinishedLatch.countDown()
            }
        }

        pageFinishedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        Thread.sleep(SETTLE_MS)

        handler.post {
            webView?.stopLoading()
            webView?.destroy()
        }

        return synchronized(candidates) { candidates.toList() }
    }
}
