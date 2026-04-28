package eu.kanade.tachiyomi.extension.es.shadowmanga.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class ImageFallbackInterceptor(
    private val cdnHosts: List<String>,
    private val fallbackHost: String,
    private val fallbackPrefix: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        val currentHost = url.host
        val isTargetHost = currentHost in cdnHosts

        val response = chain.proceed(request)

        if (!isTargetHost || response.isSuccessful) {
            return response
        }

        val path = url.encodedPath

        val key = when {
            path.startsWith(fallbackPrefix) -> path.removePrefix(fallbackPrefix)
            else -> path.removePrefix("/")
        }

        if (key.isBlank()) {
            return response
        }

        response.close()

        val nextCdn = cdnHosts.firstOrNull { it != currentHost }

        if (nextCdn != null) {
            val cdnUrl = url.newBuilder()
                .host(nextCdn)
                .build()

            val cdnRequest = request.newBuilder()
                .url(cdnUrl)
                .build()

            val cdnResponse = chain.proceed(cdnRequest)

            if (cdnResponse.isSuccessful) {
                return cdnResponse
            }

            cdnResponse.close()
        }

        val fallbackUrl = url.newBuilder()
            .scheme("https")
            .host(fallbackHost)
            .encodedPath("$fallbackPrefix$key")
            .encodedQuery(url.encodedQuery)
            .build()

        val fallbackRequest = request.newBuilder()
            .url(fallbackUrl)
            .build()

        return chain.proceed(fallbackRequest)
    }
}
