package eu.kanade.tachiyomi.extension.zh.favcomic

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ImageDecryptInterceptor : Interceptor {

    private val keyBytes by lazy { Base64.decode("NlgrYjYuRT5ic1hifSs9Tg==", Base64.DEFAULT) }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isSuccessful || request.url.fragment != "true") {
            return response
        }

        val decryptedBytes = decryptAesCbc(response.body.bytes())

        val mimeType = getMimeType(request.url.toString())

        return response.newBuilder().code(200).message("OK")
            .header("Content-Type", mimeType)
            .body(decryptedBytes.toResponseBody(mimeType.toMediaType()))
            .build()
    }

    private fun decryptAesCbc(encrypted: ByteArray): ByteArray {
        val iv = encrypted.copyOfRange(0, 16)
        val ciphertext = encrypted.copyOfRange(16, encrypted.size)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getMimeType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".gif") -> "image/gif"
            else -> "application/octet-stream"
        }
    }
}
