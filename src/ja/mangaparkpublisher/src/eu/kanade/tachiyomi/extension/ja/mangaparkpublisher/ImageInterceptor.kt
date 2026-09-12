package eu.kanade.tachiyomi.extension.ja.mangaparkpublisher

import android.util.Base64
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

        val key = Base64.decode(fragment, Base64.DEFAULT)
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
