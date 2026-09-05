package eu.kanade.tachiyomi.extension.en.inkr

import keiyoushi.zip.fixedLength
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.endsWith(".ikc")) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val source = response.body.source()
        // First 4 bytes are little-endian plaintext size; next 16 are IV. AES-CBC with no padding.
        val originalSize = source.readIntLe().toLong()
        val iv = source.readByteArray(IV_SIZE.toLong())
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY, ALGORITHM), IvParameterSpec(iv))
        }

        return response.newBuilder()
            .body(
                source.cipherSource(cipher)
                    .fixedLength(originalSize)
                    .buffer()
                    .asResponseBody(MEDIA_TYPE, originalSize),
            )
            .build()
    }

    companion object {
        private const val IV_SIZE = 16
        private const val ALGORITHM = "AES"
        private const val TRANSFORM = "AES/CBC/NoPadding"
        private const val KEY_XOR = 0x5A
        private val MEDIA_TYPE = "image/webp".toMediaType()

        private val ENCODED_KEY = byteArrayOf(
            0x1F, 0x17, 0x0B, 0x11, 0x39, 0x2D, 0x03, 0x2B,
            0x0B, 0x2E, 0x36, 0x12, 0x68, 0x63, 0x11, 0x20,
            0x09, 0x00, 0x29, 0x1E, 0x35, 0x38, 0x12, 0x16,
            0x6B, 0x37, 0x12, 0x2C, 0x20, 0x35, 0x2E, 0x36,
        )

        private val KEY = ByteArray(ENCODED_KEY.size) { i ->
            ((ENCODED_KEY[i].toInt() and 0xFF) xor KEY_XOR).toByte()
        }
    }
}
