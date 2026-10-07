package eu.kanade.tachiyomi.extension.ja.mangaboxme

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

        if (fragment.isNullOrEmpty() || !response.isSuccessful) return response

        val mask = fragment.toByte()
        val responseBody = response.body
        val decrypted = object : ForwardingSource(responseBody.source()) {
            private val cursor = Buffer.UnsafeCursor()

            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read <= 0L) return read

                sink.readAndWriteUnsafe(cursor).use {
                    var length = it.seek(sink.size - read)
                    while (length != -1) {
                        val data = it.data!!
                        for (i in it.start until it.end) {
                            data[i] = data[i] xor mask
                        }
                        length = it.next()
                    }
                }
                return read
            }
        }

        val body = decrypted.buffer().asResponseBody(responseBody.contentType(), responseBody.contentLength())
        return response.newBuilder()
            .body(body)
            .build()
    }
}
