package eu.kanade.tachiyomi.extension.all.manhuarm.interceptors

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Headers
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OcrUrlInterceptor(private val headers: Headers) {

    private val context: Application by injectLazy()

    private val handler = Handler(Looper.getMainLooper())

    fun getUrl(url: String): String? {
        val latch = CountDownLatch(1)
        var ocrUrl: String? = null
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

            webview.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val requestUrl = request?.url?.toString()
                        ?: return super.shouldInterceptRequest(view, request)
                    if (ocrUrl == null && requestUrl.contains("fetch-ocr.php")) {
                        ocrUrl = requestUrl
                        latch.countDown()
                        view?.post { view.stopLoading() }
                    }
                    return super.shouldInterceptRequest(view, request)
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

        return ocrUrl
    }
}
