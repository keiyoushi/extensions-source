package eu.kanade.tachiyomi.extension.en.kodansha

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer

// Chapter page images are XOR-obfuscated by the Azuki reader
class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful || chain.request().url.host != CONTENT_HOST) {
            return response
        }
        val body = response.body
        val decoded = XorSource(body.source()).buffer().asResponseBody(body.contentType(), body.contentLength())
        return response.newBuilder().body(decoded).build()
    }

    private class XorSource(delegate: Source) : ForwardingSource(delegate) {
        private val chunk = Buffer()

        override fun read(sink: Buffer, byteCount: Long): Long {
            val read = super.read(chunk, byteCount)
            if (read > 0) {
                val bytes = chunk.readByteArray()
                for (i in bytes.indices) {
                    bytes[i] = (bytes[i].toInt() xor 174).toByte()
                }
                sink.write(bytes)
            }
            return read
        }
    }

    companion object {
        private const val CONTENT_HOST = "production.image-content.azuki.co"
    }
}
