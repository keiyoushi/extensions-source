package eu.kanade.tachiyomi.extension.en.infinityscans

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

const val SESSION_COOKIE = "__Secure-infinityscans.data"

class WebviewInterceptor(private val baseUrl: String) : Interceptor {

    private val context: Application by injectLazy()
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.hasSessionCookie()) {
            response.close()

            resolveInWebview(request.header("User-Agent"))

            val res = chain.proceed(request)
            // If webview failed
            if (res.hasSessionCookie()) {
                response.close()
                throw IOException("Solve webview Captcha and refresh.")
            }
            return res
        }
        return response
    }

    private fun resolveInWebview(userAgent: String?) {
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        var hasSetCookies = false

        handler.post {
            val webview = WebView(context)
            webView = webview
            with(webview.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = false
                loadWithOverviewMode = false
                userAgentString = userAgent
            }

            webview.webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                    if (request.method == "POST" && request.url.toString().contains("/api/validate")) {
                        hasSetCookies = true
                    } else if (request.url.toString().contains(baseUrl) && hasSetCookies) {
                        latch.countDown()
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            webview.loadUrl("$baseUrl/")
        }

        latch.await(20, TimeUnit.SECONDS)

        handler.post {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
}

fun Response.hasSessionCookie(): Boolean = headers("Set-Cookie").any { it.startsWith(SESSION_COOKIE) }
