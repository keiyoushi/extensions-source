package eu.kanade.tachiyomi.extension.all.ososedki

import okhttp3.Interceptor
import okhttp3.Response

fun imageFallbackInterceptor(chain: Interceptor.Chain): Response {
    val response = chain.proceed(chain.request())
    if (response.code != 404) {
        return response
    }

    val url = response.request.url
    if (url.pathSegments.size < 3 || url.pathSegments[0] != "images" || url.pathSegments[1] != "a" || url.pathSegments[2] != "1280") {
        return response
    }

    response.close()
    val newUrl = url.newBuilder()
        .setPathSegment(2, "604")
        .build()
    val newRequest = response.request.newBuilder()
        .url(newUrl)
        .build()

    return chain.proceed(newRequest)
}
