package eu.kanade.tachiyomi.extension.ja.zebrack

import keiyoushi.utils.decodeHex
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import kotlin.experimental.xor

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (!response.isSuccessful || fragment.isNullOrEmpty() || !fragment.contains("key=")) return response

        val key = fragment.substringAfter("key=").decodeHex()
        if (key.isEmpty()) return response
        val responseBody = response.body
        val decrypted = object : ForwardingSource(responseBody.source()) {
            private val buffer = Buffer()
            private var index = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(buffer, byteCount)
                if (read == -1L) return -1L

                val bytes = buffer.readByteArray()
                for (i in bytes.indices) {
                    bytes[i] = bytes[i] xor key[(index++ % key.size).toInt()]
                }

                sink.write(bytes)
                return read
            }
        }

        val body = decrypted.buffer().asResponseBody(responseBody.contentType(), responseBody.contentLength())
        return response.newBuilder()
            .body(body)
            .build()
    }
}
