package eu.kanade.tachiyomi.extension.zh.bookwalkertw

import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.runWebViewBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Passes the AWS WAF challenge the site puts in front of its search pages, in a WebView whose GETs to the site go
 * through OkHttp, without WebView's X-Requested-With. Its token only lasts a few minutes.
 */
internal class WafInterceptor(private val client: () -> OkHttpClient) : Interceptor {

    // The WebView's own requests to the site don't come back here
    private val proxyClient by lazy {
        client().newBuilder().apply { interceptors().remove(this@WafInterceptor) }.build()
    }

    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        // Too many searches in a row: the site wants a human
        if (response.header("x-amzn-waf-action") == "captcha") {
            response.close()
            throw IOException("Solve the site's check in WebView, or wait a few minutes")
        }
        if (!response.isChallenge()) return response
        response.close()
        synchronized(lock) {
            // Passed meanwhile for another request
            val retry = chain.proceed(request)
            if (!retry.isChallenge()) return retry
            retry.close()
            pass(chain, request)
        }
        return chain.proceed(request)
    }

    private fun pass(chain: Interceptor.Chain, request: Request) {
        val url = request.url.toString()
        val previous = token(url)
        try {
            runWebViewBlocking<Unit>(chain.call(), TIMEOUT) {
                userAgent = request.header("User-Agent")!!
                blockImages = true
                if (originMatchedHeaderSupported) setOriginMatchedHeader("X-Requested-With", "XMLHttpRequest", setOf("https://*.token.awswaf.com"))
                interceptRequest {
                    when {
                        it.url.host != request.url.host -> null
                        // The challenge reloads the page once its token is set: done, without loading it twice
                        it.isForMainFrame && token(url) != previous -> {
                            resolve(Unit)
                            WebResourceResponse(null, null, null)
                        }
                        else -> proxyRequest(it, proxyClient) { removeHeader("X-Requested-With") }
                    }
                }
                poll(POLL_INTERVAL) {
                    if (token(url).let { it != null && it != previous }) resolve(Unit)
                }
                loadUrl(url)
            }
        } catch (e: WebViewTimeoutException) {
            throw IOException("The site's check did not pass, try again later", e)
        }
    }

    private fun token(url: String): String? = CookieManager.getInstance().getCookie(url)
        ?.split("; ")
        ?.firstOrNull { it.startsWith("aws-waf-token=") }

    private fun Response.isChallenge() = code == 202 && header("x-amzn-waf-action") == "challenge"

    companion object {
        private val TIMEOUT = 30.seconds
        private val POLL_INTERVAL = 250.milliseconds
    }
}
