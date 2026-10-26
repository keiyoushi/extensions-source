package eu.kanade.tachiyomi.extension.all.deviantart

import eu.kanade.tachiyomi.source.model.Page
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

internal fun createImagePage(index: Int, readingUrl: String?): Page {
    if (readingUrl == null) return Page(index)

    val fallback = readingUrl.toHttpUrl()
    val original = fallback.newBuilder()
        .encodedPath(fallback.encodedPath.substringBefore("/v1/"))
        .build()

    return Page(index, url = fallback.toString(), imageUrl = original.toString())
}

internal class OriginalImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val page = request.tag(Page::class.java)

        if (response.code != 403 || page == null || page.url.isEmpty() || page.url == request.url.toString()) {
            return response
        }

        response.close()
        return chain.proceed(request.newBuilder().url(page.url).build())
    }
}
