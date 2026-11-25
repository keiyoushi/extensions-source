package eu.kanade.tachiyomi.extension.en.madaradex

import keiyoushi.utils.runWebViewBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

internal class MdxAuthInterceptor : Interceptor {

    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code != 403 || request.url.host != CDN_HOST) return response
        response.close()

        val chapterUrl = request.header("Referer") ?: throw IOException(ERROR)

        try {
            runWebViewBlocking<Unit>(chain.call(), 15.seconds) {
                userAgent = request.header("User-Agent")!!
                javaScriptEnabled = true

                var authRequested = false

                interceptRequest {
                    when {
                        it.url?.encodedPath?.endsWith("/admin-ajax.php") == true -> authRequested = true
                        // Auth cookies should be populated once a page is fetched
                        authRequested && it.url?.host == CDN_HOST -> resolve(Unit)
                    }
                    null
                }

                loadUrl(chapterUrl)
            }
        } catch (e: Exception) {
            throw IOException(ERROR, e)
        }

        val retry = chain.proceed(request)
        if (retry.code == 403) {
            retry.close()
            throw IOException(ERROR)
        }
        return retry
    }

    companion object {
        private const val CDN_HOST = "cdn.madaradex.org"
        private const val ERROR = "Open chapter in WebView and retry"
    }
}
