package eu.kanade.tachiyomi.extension.all.manhuarm.interceptors

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Headers
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OcrUrlInterceptor(private val headers: Headers) {

    private val context: Application by injectLazy()

    private val handler = Handler(Looper.getMainLooper())

    data class OcrRequest(val url: String, val body: String)

    private val bridgeName = ('a'..'z').shuffled().take(10).joinToString("")

    fun getOcrRequest(url: String): OcrRequest? {
        val latch = CountDownLatch(1)
        var ocrRequest: OcrRequest? = null
        var webView: WebView? = null

        handler.post {
            val webview = WebView(context)
            webView = webview
            with(webview.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = false
                loadWithOverviewMode = false
                userAgentString = headers["User-Agent"]
            }

            webview.addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onFetch(url: String, body: String) {
                        if (ocrRequest == null && url.contains("fetch-ocr.php")) {
                            ocrRequest = OcrRequest(url, body)
                            latch.countDown()
                        }
                    }
                },
                bridgeName,
            )

            webview.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(
                        """
                        (function() {
                            const oldFetch = window.fetch;
                            window.fetch = function() {
                                const url = arguments[0];
                                const options = arguments[1];
                                if (url.includes('fetch-ocr.php') && options && options.body) {
                                    $bridgeName.onFetch(url, options.body);
                                }
                                return oldFetch.apply(this, arguments);
                            };
                        })();
                        """.trimIndent(),
                        null,
                    )
                }
            }

            webview.loadUrl(url, headers.toMultimap().mapValues { it.value.first() })
        }

        val completed = latch.await(10, TimeUnit.SECONDS)

        handler.post {
            webView?.apply {
                stopLoading()
                removeAllViews()
                destroy()
            }
            webView = null
        }

        if (!completed) return null

        return ocrRequest
    }
}
