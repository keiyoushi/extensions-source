package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object TokenExtractor {

    private const val TIMEOUT_SECONDS = 30L
    private const val WEBVIEW_WIDTH = 1080
    private const val WEBVIEW_HEIGHT = 1920

    data class Token(val header: String, val value: String)

    @Synchronized
    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    fun extract(siteUrl: String, userAgent: String? = null): Token? {
        val context = Injekt.get<Application>()
        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        var result: Token? = null
        var webView: WebView? = null

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
                if (!userAgent.isNullOrBlank()) userAgentString = userAgent
            }

            val bridge = object : Any() {
                @JavascriptInterface
                fun onToken(header: String, value: String) {
                    if (latch.count > 0L) {
                        result = Token(header, value)
                        latch.countDown()
                    }
                }
            }

            wv.addJavascriptInterface(bridge, "TokenBridge")

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(
                        """
                        (function() {
                            const _std = new Set([
                                'accept', 'accept-language', 'accept-encoding', 'content-type',
                                'content-length', 'authorization', 'user-agent', 'referer', 'origin',
                                'cookie', 'cache-control', 'pragma', 'connection', 'host', 'dnt',
                                'x-csrf-token', 'x-requested-with', 'priority', 'sec-fetch-dest',
                                'sec-fetch-mode', 'sec-fetch-site', 'sec-ch-ua', 'sec-ch-ua-mobile',
                                'sec-ch-ua-platform',
                            ]);
                            const _report = function(k, v) {
                                if (k && v && !_std.has(String(k).toLowerCase()) && String(v).length < 60) {
                                    TokenBridge.onToken(k, v);
                                }
                            };
                            const _fetch = window.fetch;
                            window.fetch = function(input, init) {
                                const headers = (init && init.headers) ? init.headers : {};
                                const entries = headers instanceof Headers
                                    ? [...headers.entries()]
                                    : Object.entries(headers);
                                for (const [k, v] of entries) { _report(k, v); }
                                return _fetch.apply(this, arguments);
                            };
                            const _xhr = XMLHttpRequest.prototype.setRequestHeader;
                            XMLHttpRequest.prototype.setRequestHeader = function(k, v) {
                                _report(k, v);
                                return _xhr.apply(this, arguments);
                            };
                        })();
                        """.trimIndent(),
                        null,
                    )
                }
            }

            wv.loadUrl(siteUrl)
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        handler.post {
            webView?.stopLoading()
            webView?.destroy()
        }

        return result
    }
}
