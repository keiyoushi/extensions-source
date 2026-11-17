package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import android.webkit.WebResourceResponse
import keiyoushi.utils.runWebViewBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.ByteArrayInputStream
import kotlin.time.Duration.Companion.seconds

/**
 * Sends the WebView's client hint headers (sec-ch-ua*) with requests to the site, like MangaFire does, so the client's
 * requests match the WebView that the cookies came from. The hints are read once from a WebView request that is
 * answered locally.
 */
class ClientHintsInterceptor(private val baseUrl: String, private val isSiteHost: (HttpUrl) -> Boolean) : Interceptor {

    @Volatile
    private var hints: Map<String, String>? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isSiteHost(request.url)) return chain.proceed(request)

        val hints = hints ?: synchronized(this) {
            hints ?: capture(chain, request.header("User-Agent").orEmpty()).also { hints = it }
        }
        return chain.proceed(
            request.newBuilder().apply { hints.forEach { (name, value) -> header(name, value) } }.build(),
        )
    }

    private fun capture(chain: Interceptor.Chain, userAgent: String): Map<String, String> = runWebViewBlocking(chain.call(), timeout = 15.seconds) {
        this.userAgent = userAgent
        interceptRequest { request ->
            val found = request.requestHeaders.filterKeys { it.startsWith("sec-ch-ua", ignoreCase = true) }
            if (found.isNotEmpty()) resolve(found)
            WebResourceResponse("text/plain", null, 204, "No Content", null, ByteArrayInputStream(ByteArray(0)))
        }
        loadUrl("$baseUrl/robots.txt")
    }
}
