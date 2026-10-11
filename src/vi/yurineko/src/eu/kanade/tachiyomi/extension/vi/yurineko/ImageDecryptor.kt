package eu.kanade.tachiyomi.extension.vi.yurineko

import keiyoushi.lib.xorinterceptor.xor
import keiyoushi.utils.decodeHex
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
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
        val keyBytes = key.decodeHex()
        if (keyBytes.isEmpty()) return response

        val contentType = response.header(CONTENT_TYPE_HEADER) ?: DEFAULT_CONTENT_TYPE
        val decrypted = response.body.source().xor(keyBytes).buffer().asResponseBody(contentType.toMediaType(), response.body.contentLength())

        return response.newBuilder()
            .body(decrypted)
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
}
