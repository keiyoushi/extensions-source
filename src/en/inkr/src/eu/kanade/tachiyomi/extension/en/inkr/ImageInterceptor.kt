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
        private val MEDIA_TYPE = "image/webp".toMediaType()

        // Fixed client key used by INKR .ikc images (CyberChef recipe from issue #223)
        private val KEY = byteArrayOf(
            0x45, 0x4D, 0x51, 0x4B, 0x63, 0x77, 0x59, 0x71,
            0x51, 0x74, 0x6C, 0x48, 0x32, 0x39, 0x4B, 0x7A,
            0x53, 0x5A, 0x73, 0x44, 0x6F, 0x62, 0x48, 0x4C,
            0x31, 0x6D, 0x48, 0x76, 0x7A, 0x6F, 0x74, 0x6C,
        )
    }
}
