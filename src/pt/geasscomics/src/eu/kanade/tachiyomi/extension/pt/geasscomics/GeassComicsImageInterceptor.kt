package eu.kanade.tachiyomi.extension.pt.geasscomics

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class GeassComicsImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.url.pathSegments.contains("image") && request.url.pathSegments.contains("chapter")) {
            val encryptedBytes = response.body.bytes()

            try {
                val decryptedBytes = decryptImage(encryptedBytes)

                val mediaType = when {
                    decryptedBytes.size >= 4 && decryptedBytes[0] == 0x89.toByte() && decryptedBytes[1] == 0x50.toByte() -> "image/png"
                    decryptedBytes.size >= 3 && decryptedBytes[0] == 0xFF.toByte() && decryptedBytes[1] == 0xD8.toByte() && decryptedBytes[2] == 0xFF.toByte() -> "image/jpeg"
                    decryptedBytes.size >= 3 && decryptedBytes[0] == 0x47.toByte() && decryptedBytes[1] == 0x49.toByte() && decryptedBytes[2] == 0x46.toByte() -> "image/gif"
                    decryptedBytes.size >= 12 && decryptedBytes[8] == 0x57.toByte() && decryptedBytes[9] == 0x45.toByte() && decryptedBytes[10] == 0x42.toByte() && decryptedBytes[11] == 0x50.toByte() -> "image/webp"
                    else -> "image/jpeg"
                }

                return response.newBuilder()
                    .body(decryptedBytes.toResponseBody(mediaType.toMediaTypeOrNull()))
                    .build()
            } catch (e: Exception) {
                throw java.io.IOException("Falha ao descriptografar imagem: ${e.message}")
            }
        }

        return response
    }

    private fun decryptImage(data: ByteArray): ByteArray {
        if (data.size < 12) throw IllegalArgumentException("Dados inválidos: menor que o tamanho do IV")

        val iv = data.copyOfRange(0, 12)
        val ciphertext = data.copyOfRange(12, data.size)

        val key = deriveKey("4f8d2a7b9c6e1f3a5b0c9e2d7a6b1c3f8e4d2a9b7c6f1e3a5b0c9d2e7f6a1b39")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(password: String): SecretKeySpec {
        val salt = "manga-app-salt".toByteArray(Charsets.UTF_8)

        val spec = PBEKeySpec(password.toCharArray(), salt, 30000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val tmp = factory.generateSecret(spec)

        return SecretKeySpec(tmp.encoded, "AES")
    }
}
