package eu.kanade.tachiyomi.extension.ja.mechacomi

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty() || !response.isSuccessful) return response

        TODO()

        val buffer = Buffer()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    companion object {
        private val MEDIA_TYPE = "image/webp".toMediaType()
    }
}
