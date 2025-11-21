package eu.kanade.tachiyomi.extension.en.spyfakku

import android.annotation.SuppressLint
import android.app.Application
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object AnibusInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        return if (request.url.host.contains("airdns")) {
            val document = Jsoup.parse(
                response.peekBody(Long.MAX_VALUE).string(),
                request.url.toString(),
            )
            if (document.selectFirst("script#anubis_challenge") != null) {
                response.close()
                if (!resolveInWebView(request)) {
                    throw IOException("Failed to resolve challenge in WebView")
                } else {
                    chain.proceed(request)
                }
            } else {
                response
            }
        } else {
            response
        }
    }

    @Synchronized
    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveInWebView(request: Request): Boolean {
        val context = Injekt.get<Application>()
        val cookieManager = CookieManager.getInstance()
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        val handler = Handler(Looper.getMainLooper())

        handler.post {
            val webview = WebView(context)
                .also { webView = it }

            with(webview.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                userAgentString = request.header("User-Agent")
            }
            webview.webViewClient = object : WebViewClient() {
                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: SslError,
                ) {
                    handler.proceed()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val cookie = cookieManager.getCookie(url)
                        .split("; ").map { it.split("=", limit = 2) }
                    val auth = cookie.firstOrNull { it.first().contains("anubis-auth") && it.last().isNotBlank() }
                    if (auth != null) {
                        latch.countDown()
                    }
                }
            }

            webview.loadUrl(request.url.toString())
        }

        latch.await(20, TimeUnit.SECONDS)

        handler.post {
            webView?.stopLoading()
            webView?.destroy()
        }

        return latch.count != 1L
    }
}
