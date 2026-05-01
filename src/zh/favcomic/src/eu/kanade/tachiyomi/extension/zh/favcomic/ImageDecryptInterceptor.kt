package eu.kanade.tachiyomi.extension.zh.favcomic

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ImageDecryptInterceptor : Interceptor {

    private val keyBytes = Base64.decode("NlgrYjYuRT5ic1hifSs9Tg==", Base64.DEFAULT)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isSuccessful || request.url.fragment != "true") {
            return response
        }

        val source = response.body.source()
        val iv = source.readByteArray(16)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))

        val contentType = getMimeType(request.url.toString()).toMediaType()
        val body = source.cipherSource(cipher).buffer().asResponseBody(contentType)

        return response.newBuilder().body(body).build()
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
