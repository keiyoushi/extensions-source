package eu.kanade.tachiyomi.extension.zh.baozimanhua

import okhttp3.Interceptor
import okhttp3.Response

// The baozimh.com image origin lags behind the site's CDN for new chapters
object ImageFallbackInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val host = request.url.host
        if (response.code != 404 || !host.endsWith(".baozimh.com") || request.url.pathSegments.first() != "scomic") {
            return response
        }
        response.close()
        val cdnUrl = request.url.newBuilder().host(host.removeSuffix(".baozimh.com") + ".baozicdn.com").build()
        return chain.proceed(request.newBuilder().url(cdnUrl).build())
    }
}
