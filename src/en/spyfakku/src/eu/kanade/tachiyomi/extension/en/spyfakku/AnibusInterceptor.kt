package eu.kanade.tachiyomi.extension.en.spyfakku

import android.webkit.CookieManager
import keiyoushi.utils.runWebViewBlocking
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object AnibusInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        return if (request.url.host.contains("airdns")) {
            // Limited peek size to avoid OOM
            val document = Jsoup.parse(
                response.peekBody(1024 * 1024 * 5).string(),
                request.url.toString(),
            )

            if (document.selectFirst("script#anubis_challenge") != null) {
                response.close()
                if (!resolveInWebView(request, chain.call())) {
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
    private fun resolveInWebView(request: Request, call: Call): Boolean = runCatching {
        val cookieManager = CookieManager.getInstance()
        runWebViewBlocking<Unit>(call, timeout = 20.seconds) {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgent = request.header("User-Agent")!!

            onPageFinished { url ->
                poll(500.milliseconds) {
                    val cookie = cookieManager.getCookie(url)
                        ?.split("; ")
                        ?.map { it.split("=", limit = 2) }
                        ?: emptyList()

                    val auth = cookie.firstOrNull {
                        it.first().contains("anubis-auth") && it.last().isNotBlank()
                    }

                    if (auth != null) {
                        resolve(Unit)
                    }
                }
            }

            loadUrl(request.url.toString())
        }

        true
    }.getOrElse {
        false
    }
}
