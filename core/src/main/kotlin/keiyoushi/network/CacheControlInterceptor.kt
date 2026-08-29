package keiyoushi.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Widens a cacheable response's `Cache-Control` to the request's own `max-age` when the origin
 * didn't already declare a usable one, so OkHttp's disk cache doesn't revalidate on every access.
 */
internal class CacheControlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val requestMaxAge = request.cacheControl.maxAgeSeconds
        val cacheControl = response.header("Cache-Control")
        if (requestMaxAge <= 0 || !response.isSuccessful || cacheControl == null || !shouldOverride(cacheControl)) {
            return response
        }

        return response.newBuilder()
            .header("Cache-Control", "public, max-age=$requestMaxAge")
            .build()
    }

    private fun shouldOverride(cacheControl: String): Boolean {
        val directives = cacheControl.split(",").map { it.trim().lowercase() }
        if (directives.any { it == "no-store" || it == "no-cache" || it == "private" }) return false
        if (directives.any { it.startsWith("max-age=") }) return false
        return true
    }
}
