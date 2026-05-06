package eu.kanade.tachiyomi.extension.ja.mangamee

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (!response.isSuccessful || fragment.isNullOrEmpty() || !fragment.contains("key=")) return response

        val keyBytes = fragment.substringAfter("key=").decodeHex()
        val bytes = response.body.bytes()
        for (i in bytes.indices) {
            bytes[i] = (bytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }

        val buffer = Buffer().write(bytes)
        val body = buffer.asResponseBody(response.body.contentType(), buffer.size)

        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
