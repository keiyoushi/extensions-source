package eu.kanade.tachiyomi.extension.ja.zebrack

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment
        if (!response.isSuccessful || fragment.isNullOrEmpty() || !fragment.contains("key=")) {
            return response
        }

        val key = fragment.substringAfter("key=")
        val bytes = response.body.bytes()
        val keyBytes = key.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        for (i in bytes.indices) {
            bytes[i] = (bytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }

        val buffer = Buffer().write(bytes)
        val body = buffer.asResponseBody(response.body.contentType(), buffer.size)

        return response.newBuilder()
            .body(body)
            .build()
    }
}
