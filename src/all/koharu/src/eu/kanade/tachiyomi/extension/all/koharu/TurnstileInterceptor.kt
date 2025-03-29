package eu.kanade.tachiyomi.extension.all.koharu

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.extension.all.koharu.Koharu.Companion.authorization
import eu.kanade.tachiyomi.extension.all.koharu.Koharu.Companion.token
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Cloudflare Turnstile interceptor */
class TurnstileInterceptor(
    private val client: OkHttpClient,
    private val domainUrl: String,
    private val authUrl: String,
    private val userAgent: String?,
) : Interceptor {

    private val context: Application by injectLazy()
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private val lazyHeaders by lazy {
        Headers.Builder().apply {
            set("User-Agent", userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.105 Safari/537.36")
            set("Referer", "$domainUrl/")
            set("Origin", domainUrl)
        }.build()
    }

    private fun authHeaders(authorization: String) =
        Headers.Builder().apply {
            set("User-Agent", userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.105 Safari/537.36")
            set("Referer", "$domainUrl/")
            set("Origin", domainUrl)
            set("Authorization", authorization)
        }.build()

    private val authorizedRequestRegex by lazy { Regex("""(.+\?crt=)(.*)""") }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val url = request.url.toString()
        val matchResult = authorizedRequestRegex.find(url) ?: return chain.proceed(request)
        if (matchResult.groupValues.size == 3) {
            val requestingUrl = matchResult.groupValues[1]
            val crt = matchResult.groupValues[2]
            var newResponse: Response

            if (crt.isNotBlank() && crt != "null") {
                // Token already set in URL, just make the request
                newResponse = chain.proceed(request)
                if (newResponse.code !in listOf(400, 403)) return newResponse
            } else {
                // Token doesn't include, add token then make request
                if (token.isNullOrBlank()) resolveInWebview()
                val newRequest = if (request.method == "POST") {
                    POST("${requestingUrl}$token", lazyHeaders)
                } else {
                    GET("${requestingUrl}$token", lazyHeaders)
                }
                newResponse = chain.proceed(newRequest)
                if (newResponse.code !in listOf(400, 403)) return newResponse
            }
            newResponse.close()

            // Request failed, refresh token then try again
            clearToken()
            token = null
            resolveInWebview()
            val newRequest = if (request.method == "POST") {
                POST("${requestingUrl}$token", lazyHeaders)
            } else {
                GET("${requestingUrl}$token", lazyHeaders)
            }
            newResponse = chain.proceed(newRequest)
            if (newResponse.code !in listOf(400, 403)) return newResponse
            throw IOException("Open webview once to refresh token (${newResponse.code})")
        }
        return chain.proceed(request)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun resolveInWebview(): Pair<String?, String?> {
        val latch = CountDownLatch(1)
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
            }

            webview.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val authHeader = request?.requestHeaders?.get("Authorization")
                    if (request?.url.toString().contains(authUrl) && authHeader != null) {
                        authorization = authHeader
                        if (request.method == "POST") {
                            // Authorize & requesting a new token.
                            // `authorization` here should be in format: Bearer <authorization>
                            try {
                                val noRedirectClient = client.newBuilder().followRedirects(false).build()
                                val authHeaders = authHeaders(authHeader)
                                val response = runBlocking(Dispatchers.IO) {
                                    noRedirectClient.newCall(POST(authUrl, authHeaders)).execute()
                                }
                                response.use {
                                    if (response.isSuccessful) {
                                        with(response) {
                                            token = body.string()
                                                .removeSurrounding("\"")
                                        }
                                    }
                                }
                            } catch (_: IOException) {
                            } finally {
                                latch.countDown()
                            }
                        }
                        if (request.method == "GET") {
                            // Site is trying to recheck old token validation here.
                            // If it fails then site will request a new one using POST method.
                            // But we will check it ourselves.
                            // Normally this might not occur because old token should already be acquired & rechecked via onPageFinished.
                            // `authorization` here should be in format: Bearer <token>
                            val oldToken = authorization
                                ?.substringAfterLast(" ")
                            if (oldToken != null && recheckTokenValid(oldToken)) {
                                token = oldToken
                                latch.countDown()
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                /**
                 * Read the saved token in localStorage and use it.
                 * This token might already expired. Normally site will check token for expiration with a GET request.
                 * Here will will recheck it ourselves.
                 */
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (view == null) return
                    val script = "javascript:localStorage['clearance']"
                    view.evaluateJavascript(script) {
                        // Avoid overwrite newly requested token
                        if (!it.isNullOrBlank() && it != "null" && token.isNullOrBlank()) {
                            val oldToken = it
                                .removeSurrounding("\"")
                            if (recheckTokenValid(oldToken)) {
                                token = oldToken
                                latch.countDown()
                            }
                        }
                    }
                }

                private fun recheckTokenValid(token: String): Boolean {
                    try {
                        val noRedirectClient = client.newBuilder().followRedirects(false).build()
                        val authHeaders = authHeaders("Bearer $token")
                        val response = runBlocking(Dispatchers.IO) {
                            noRedirectClient.newCall(GET(authUrl, authHeaders)).execute()
                        }
                        response.use {
                            if (response.isSuccessful) {
                                return true
                            }
                        }
                    } catch (_: IOException) {
                    }
                    return false
                }
            }

            webview.loadUrl("$domainUrl/")
        }

        latch.await(20, TimeUnit.SECONDS)

        handler.post {
            // One last try to read the token from localStorage, in case it got updated last minute.
            if (token.isNullOrBlank()) {
                val script = "javascript:localStorage['clearance']"
                webView?.evaluateJavascript(script) {
                    if (!it.isNullOrBlank() && it != "null") {
                        token = it
                            .removeSurrounding("\"")
                    }
                }
            }

            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }

        return token to authorization
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun clearToken() {
        val latch = CountDownLatch(1)
        handler.post {
            val webView = WebView(context)
            with(webView.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (view == null) return
                    val script = "javascript:localStorage.clear()"
                    view.evaluateJavascript(script) {
                        token = null
                        view.stopLoading()
                        view.destroy()
                        latch.countDown()
                    }
                }
            }
            webView.loadUrl(domainUrl)
        }
        latch.await(20, TimeUnit.SECONDS)
    }
}
