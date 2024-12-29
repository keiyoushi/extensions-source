package eu.kanade.tachiyomi.extension.en.erosscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class ErosScans : MangaThemesia(
    "Eros Scans",
    "https://eros-toons.xyz",
    "en",
) {
    override val client = super.client.newBuilder()
        .addInterceptor(::cdnRedirectInterceptor)
        .rateLimit(3)
        .build()

    private fun cdnRedirectInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != "erosscans.xyz") {
            return chain.proceed(request)
        }

        val newUrl = request.url.newBuilder()
            .host(baseUrl.toHttpUrl().host)
            .build()
        val newRequest = request.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}
