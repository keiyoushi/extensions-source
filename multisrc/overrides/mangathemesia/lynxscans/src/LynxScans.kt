package eu.kanade.tachiyomi.extension.en.lynxscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Interceptor
import okhttp3.Response

class LynxScans : MangaThemesia("LynxScans", "https://lynxscans.com", "en", "/comics") {
    override val versionId = 3
    override val hasProjectPage = true

    override val client = super.client.newBuilder()
        .addNetworkInterceptor(::pageSpeedRedirectIntercept)
        .rateLimit(2)
        .build()

    private fun pageSpeedRedirectIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.pathSegments.contains("wp-content") || request.method == "POST") {
            return chain.proceed(request)
        }

        val newUrl = request.url.newBuilder()
            .setQueryParameter("PageSpeed", "noscript")
            .build()

        val newRequest = request.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}
