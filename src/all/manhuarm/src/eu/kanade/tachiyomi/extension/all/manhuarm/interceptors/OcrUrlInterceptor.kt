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

class OcrUrlInterceptor(private val headers: Headers) {

    private val context: Application by injectLazy()
    private val handler = Handler(Looper.getMainLooper())
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
                    fun onFetch(url: String, body: String, headersJson: String) {
                        if (ocrRequest == null && url.contains("fetch-ocr.php")) {
                            val headerMap = mutableMapOf<String, String>()
                            try {
                                val json = JSONObject(headersJson)
                                val keys = json.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    headerMap[key] = json.getString(key)
                                }
                            } catch (_: Exception) { /* do nothing */ }

                            ocrRequest = OcrRequest(url, body, headerMap)
                            latch.countDown()
                        }
                    }
                },
                bridgeName,
            )

            webview.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) = injectScript(view)
                override fun onPageFinished(view: WebView?, url: String?) = injectScript(view)
            }

            webview.loadUrl(url, headers.toMultimap().mapValues { it.value.first() })
        }

        latch.await(10, TimeUnit.SECONDS)

        handler.post {
            webView?.apply {
                stopLoading()
                destroy()
            }
        }

        return ocrRequest
    }

    private fun injectScript(view: WebView?) {
        val utilities = """
            const origin = Math.random;
            const proxy = () => 0.01 + origin() * 0.09;

            Object.setPrototypeOf(proxy, Object.getPrototypeOf(origin));
            Object.defineProperties(proxy, {
                'name': { value: 'random', writable: false },
                'length': { value: 0, writable: false }
            });

            proxy.toString = () => "function random() {\n    [native code]\n}"

            Object.defineProperty(Math, 'random', {
                value: proxy,
                writable: false,
                configurable: false
            });

            const serializeHeaders = (h) => {
                if (!h) return "{}";
                if (h instanceof Headers) {
                    const obj = {};
                    h.forEach((v, k) => { obj[k] = v; });
                    return JSON.stringify(obj);
                }
                return JSON.stringify(h);
            };
        """.trimIndent()

        view?.evaluateJavascript(
            """
            (function() {
                $utilities

                const nativeFetch = window.fetch;
                window.fetch = function() {
                    const input = arguments[0];
                    const options = arguments[1] || {};
                    const url = typeof input === 'string' ? input : (input.url || "");

                    if (url.includes('fetch-ocr.php')) {
                        const body = options.body || "";
                        const headers = serializeHeaders(options.headers);

                        if (window.$bridgeName) {
                            window.$bridgeName.onFetch(url, body.toString(), headers);
                        }
                    }
                    return nativeFetch.apply(this, arguments);
                };
            })();
            """.trimIndent(),
            null,
        )
    }
}

data class OcrRequest(
    val url: String,
    val body: String,
    val interceptedHeaders: Map<String, String>,
)
