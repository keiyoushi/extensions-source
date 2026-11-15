package eu.kanade.tachiyomi.extension.en.infinityscans

import keiyoushi.utils.runWebViewBlocking
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

const val SESSION_COOKIE = "__Secure-infinityscans.data"

class WebviewInterceptor(private val baseUrl: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val userAgent = request.header("User-Agent")

        val response = chain.proceed(request)

        if (response.hasDeleteSessionCookie()) {
            response.close()
            resolveInWebview(chain.call(), userAgent)
            val res = chain.proceed(request)
            // If webview failed
            if (res.hasDeleteSessionCookie()) {
                res.close()
                throw IOException("Solve webview Captcha and refresh.")
            }
            return res
        }
        return response
    }

    private fun resolveInWebview(call: Call, userAgent: String?) {
        runWebViewBlocking(call, timeout = 15.seconds) {
            javaScriptEnabled = true
            domStorageEnabled = true
            this.userAgent = userAgent!!

            var hasSetCookies = false

            interceptRequest { request ->
                if (request.method == "POST" && request.url.toString().contains("/api/validate")) {
                    hasSetCookies = true
                } else if (request.url.toString().contains(baseUrl) && hasSetCookies) {
                    resolve(null)
                }
                null
            }
            loadUrl("$baseUrl/")
        }
    }
}
fun Response.hasDeleteSessionCookie(): Boolean = headers("Set-Cookie").any {
    it.startsWith(SESSION_COOKIE) && it.substringAfter(SESSION_COOKIE + "=").substringBefore(";").isEmpty()
}
