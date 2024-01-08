package eu.kanade.tachiyomi.extension.zh.baozimhorg

import okhttp3.Interceptor
import okhttp3.Response

// Temporary interceptor to handle URL redirections
object UrlInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val (type, slug) = url.pathSegments
        when (type) {
            "manga", "chapterlist" -> {}
            else -> return chain.proceed(request)
        }

        val mangaUrl = "/manga/$slug/"
        val headRequest = request.newBuilder()
            .head()
            .url(url.resolve(mangaUrl)!!)
            .build()
        // might redirect multiple times
        val headResponse = chain.proceed(headRequest)
        if (headResponse.priorResponse == null) return chain.proceed(request)

        val realSlug = headResponse.request.url.pathSegments[1]
        val newUrl = url.newBuilder().setEncodedPathSegment(1, realSlug).build()
        val newRequest = request.newBuilder().url(newUrl).build()
        return chain.proceed(newRequest)
    }
}
