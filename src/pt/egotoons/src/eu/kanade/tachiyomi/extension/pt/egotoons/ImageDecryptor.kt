package eu.kanade.tachiyomi.extension.pt.egotoons

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ImageDecryptor : Interceptor {

    private val derivedKey: ByteArray by lazy { deriveKey() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!IMAGE_URL_PATTERN.containsMatchIn(request.url.encodedPath)) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)

        if (!response.isSuccessful) {
            return response
        }

        val encryptedData = response.body.bytes()

        val decryptedData = runCatching { decrypt(encryptedData) }
            .getOrElse { return response.newBuilder().body(encryptedData.toResponseBody()).build() }

        val mediaType = detectMediaType(decryptedData)

        return response.newBuilder()
            .body(decryptedData.toResponseBody(mediaType.toMediaType()))
            .build()
    }

    private fun decrypt(encryptedData: ByteArray): ByteArray {
        val iv = encryptedData.copyOfRange(0, IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(IV_LENGTH, encryptedData.size)

        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(derivedKey, ALGORITHM), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            doFinal(ciphertext)
        }
    }

    private fun deriveKey(): ByteArray {
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec = PBEKeySpec(ENCRYPTION_KEY.toCharArray(), SALT.toByteArray(), ITERATIONS, KEY_LENGTH_BITS)
        return factory.generateSecret(spec).encoded
    }

    private fun detectMediaType(data: ByteArray): String = when {
        data.startsWith(PNG_HEADER) -> "image/png"

        data.startsWith(JPEG_HEADER) -> "image/jpeg"

        data.startsWith(GIF_HEADER) -> "image/gif"

        data.size >= 12 && data.startsWith(RIFF_HEADER) &&
            data.slice(8..11) == WEBP_MARKER -> "image/webp"

        else -> "image/jpeg"
    }

    private fun ByteArray.startsWith(prefix: List<Byte>) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    companion object {
        private const val ENCRYPTION_KEY = "4f8d2a7b9c6e1f3a5b0c9e2d7a6b1c3f8e4d2a9b7c6f1e3a5b0c9d2e7f6a1b39"
        private const val SALT = "manga-app-salt"
        private const val ITERATIONS = 30000
        private const val KEY_LENGTH_BITS = 256
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
        private val IMAGE_URL_PATTERN = Regex("""/api/manga/\d+/chapter/[\d.]+/image/\d+""")
        private val PNG_HEADER = listOf<Byte>(0x89.toByte(), 0x50, 0x4E, 0x47)
        private val JPEG_HEADER = listOf<Byte>(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val GIF_HEADER = listOf<Byte>(0x47, 0x49, 0x46)
        private val RIFF_HEADER = listOf<Byte>(0x52, 0x49, 0x46, 0x46)
        private val WEBP_MARKER = listOf<Byte>(0x57, 0x45, 0x42, 0x50)
    }
}
