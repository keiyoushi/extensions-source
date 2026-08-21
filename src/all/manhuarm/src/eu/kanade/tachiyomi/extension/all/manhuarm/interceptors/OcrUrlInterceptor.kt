package eu.kanade.tachiyomi.extension.all.manhuarm.interceptors

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Headers
import org.json.JSONObject
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Loads the real chapter page in a hidden WebView and observes the site's own OCR
 * request, without disturbing the page's anti-bot checks.
 *
 * Unlike keiyoushi's original implementation this only hooks XHR/fetch prototype
 * methods (see `assets/scripts/ocr-inject.js`) and never replaces XMLHttpRequest,
 * setTimeout, setInterval or Worker, so the site's `[native code]` detection keeps
 * passing and the captured gate headers are genuine.
 */
class OcrUrlInterceptor(private val headers: Headers) {

    private val context: Application by injectLazy()
    private val handler = Handler(Looper.getMainLooper())

    fun getOcrRequest(url: String): OcrRequest? {
        val requestLatch = CountDownLatch(1)
        val responseLatch = CountDownLatch(1)
        val captured = CapturedRequest()
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
                    fun onFetch(url: String, body: String, headersJson: String) {
                        if (captured.url == null) {
                            val headerMap = mutableMapOf<String, String>()
                            try {
                                val json = JSONObject(headersJson)
                                val keys = json.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    headerMap[key] = json.getString(key)
                                }
                            } catch (_: Exception) { /* do nothing */ }

                            captured.url = url
                            captured.body = body
                            captured.headers = headerMap
                            requestLatch.countDown()
                        }
                    }

                    @JavascriptInterface
                    fun onOcrResponse(url: String, responseText: String) {
                        if (captured.url != null && responseText.isNotBlank()) {
                            captured.responseText = responseText
                            responseLatch.countDown()
                        }
                    }
                },
                BRIDGE_NAME,
            )

            webview.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) = injectScript(view)
                override fun onPageFinished(view: WebView?, url: String?) = injectScript(view)
            }

            webview.loadUrl(url, headers.toMultimap().mapValues { it.value.first() })
        }

        // The site delays the OCR start with setTimeout(Math.random()*300+200)
        val requestCaptured = requestLatch.await(15, TimeUnit.SECONDS)
        // Give the page a moment to finish its own OCR response
        if (requestCaptured) {
            responseLatch.await(6, TimeUnit.SECONDS)
        }

        handler.post {
            webView?.apply {
                stopLoading()
                destroy()
            }
            webView = null
        }

        val capturedUrl = captured.url ?: return null

        return OcrRequest(
            url = capturedUrl,
            body = captured.body ?: "",
            interceptedHeaders = captured.headers,
            responseText = captured.responseText,
        )
    }

    private val utilities: String by lazy {
        javaClass.getResource("/assets/scripts/ocr-inject.js")!!.readText()
    }

    private fun injectScript(view: WebView?) {
        view?.evaluateJavascript(
            utilities.replace("__BRIDGE_NAME__", BRIDGE_NAME),
            null,
        )
    }

    companion object {
        private val BRIDGE_NAME = ('a'..'z').shuffled().take(10).joinToString("")
    }
}

private class CapturedRequest {
    var url: String? = null
    var body: String? = null
    var headers: Map<String, String> = emptyMap()
    var responseText: String? = null
}

data class OcrRequest(
    val url: String,
    val body: String,
    val interceptedHeaders: Map<String, String>,
    val responseText: String? = null,
)
