package eu.kanade.tachiyomi.extension.vi.yurineko

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import java.util.Base64

object ImageDecryptor {

    private const val IMAGE_KEY_HEADER = "x-ik"
    private const val CONTENT_TYPE_HEADER = "X-Ct"
    private const val DEFAULT_CONTENT_TYPE = "image/webp"

    fun interceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!request.url.encodedPath.startsWith("/api/img")) return response

        val key = request.header(IMAGE_KEY_HEADER) ?: return response
        val keyBytes = key.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (keyBytes.isEmpty()) return response

        val encrypted = response.body.source()
        val contentType = response.header(CONTENT_TYPE_HEADER) ?: DEFAULT_CONTENT_TYPE
        val decrypted = xorSource(encrypted, keyBytes)

        return response.newBuilder()
            .body(decrypted.buffer().asResponseBody(contentType.toMediaType(), response.body.contentLength()))
            .build()
    }

    fun extractKey(imageUrl: String): String? {
        val url = imageUrl.toHttpUrlOrNull() ?: return null
        val encoded = url.queryParameter("d") ?: return null
        if (encoded.isBlank()) return null
        val decoded = runCatching {
            val normalized = encoded.replace('-', '+').replace('_', '/')
            val padded = normalized.padEnd((normalized.length + 3) / 4 * 4, '=')
            String(Base64.getDecoder().decode(padded))
        }.getOrNull() ?: return null
        return decoded.substringAfter('|', "").takeIf { it.isNotBlank() }
    }

    private fun xorSource(source: Source, keyBytes: ByteArray): Source = object : Source {
        private var offset = 0L

        override fun read(sink: Buffer, byteCount: Long): Long {
            val encrypted = Buffer()
            val bytesRead = source.read(encrypted, byteCount)
            if (bytesRead == -1L) return -1L

            repeat(bytesRead.toInt()) { index ->
                val keyIndex = ((offset + index) % keyBytes.size).toInt()
                sink.writeByte(encrypted.readByte().toInt() xor keyBytes[keyIndex].toInt())
            }
            offset += bytesRead
            return bytesRead
        }

        override fun timeout(): Timeout = source.timeout()

        override fun close() = source.close()
    }
}
