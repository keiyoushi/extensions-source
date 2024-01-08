package eu.kanade.tachiyomi.extension.all.cubari

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RemoteStorageUtils {
    abstract class GenericInterceptor(private val transparent: Boolean) : Interceptor {
        private val handler = Handler(Looper.getMainLooper())

        abstract val jsScript: String

        abstract fun urlModifier(originalUrl: String): String

        internal class JsInterface(private val latch: CountDownLatch, var payload: String = "") {
            @JavascriptInterface
            fun passPayload(passedPayload: String) {
                payload = passedPayload
                latch.countDown()
            }
        }

        @Synchronized
        override fun intercept(chain: Interceptor.Chain): Response {
            try {
                val originalRequest = chain.request()
                val originalResponse = chain.proceed(originalRequest)
                return proceedWithWebView(originalRequest, originalResponse)
            } catch (e: Exception) {
                throw IOException(e)
            }
        }

        @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
        private fun proceedWithWebView(request: Request, response: Response): Response {
            val latch = CountDownLatch(1)

            var webView: WebView? = null

            val origRequestUrl = request.url.toString()
            val headers = request.headers.toMultimap().mapValues {
                it.value.getOrNull(0) ?: ""
            }.toMutableMap()
            val jsInterface = JsInterface(latch)

            handler.post {
                val webview = WebView(Injekt.get<Application>())
                webView = webview
                with(webview.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    userAgentString = request.header("User-Agent")
                }

                webview.addJavascriptInterface(jsInterface, "android")

                webview.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(jsScript) {}
                        if (transparent) {
                            latch.countDown()
                        }
                    }
                }

                webview.loadUrl(urlModifier(origRequestUrl), headers)
            }

            latch.await(TIMEOUT_SEC, TimeUnit.SECONDS)

            handler.postDelayed(
                { webView?.destroy() },
                DELAY_MILLIS * (if (transparent) 2 else 1),
            )

            return if (transparent) {
                response
            } else {
                response.newBuilder().body(jsInterface.payload.toResponseBody(response.body.contentType())).build()
            }
        }
    }

    class TagInterceptor : GenericInterceptor(true) {
        override val jsScript: String = """
           let dispatched = false;
           window.addEventListener('history-ready', function () {
             if (!dispatched) {
               dispatched = true;
               Promise.all(
                 [globalHistoryHandler.getAllPinnedSeries(), globalHistoryHandler.getAllUnpinnedSeries()]
               ).then(e => {
                 window.android.passPayload(JSON.stringify(e.flatMap(e => e)))
               });
             }
           });
           tag();
        """

        override fun urlModifier(originalUrl: String): String {
            return originalUrl.replace("/api/", "/").replace("/series/", "/")
        }
    }

    class HomeInterceptor : GenericInterceptor(false) {
        override val jsScript: String = """
           let dispatched = false;
           (function () {
             if (!dispatched) {
               dispatched = true;
               Promise.all(
                 [globalHistoryHandler.getAllPinnedSeries(), globalHistoryHandler.getAllUnpinnedSeries()]
               ).then(e => {
                 window.android.passPayload(JSON.stringify(e.flatMap(e => e) ) )
               });
             }
           })();
        """

        override fun urlModifier(originalUrl: String): String {
            return originalUrl
        }
    }

    companion object {
        const val TIMEOUT_SEC: Long = 10
        const val DELAY_MILLIS: Long = 10000
    }
}
