package eu.kanade.tachiyomi.extension.all.mangaup

import okhttp3.Interceptor
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
        val url = request.url
        val fragment = url.fragment

        if (fragment.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val parts = fragment.split("#")
        val key = parts.getOrNull(0)?.removePrefix("key=")
        val iv = parts.getOrNull(1)?.removePrefix("iv=")

        if (key.isNullOrEmpty() || iv.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)

        if (!response.isSuccessful) return response

        val secretKey = SecretKeySpec(key.decodeHex(), "AES")
        val ivSpec = IvParameterSpec(iv.decodeHex())
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        val body = response.body.source().cipherSource(cipher).buffer().asResponseBody(response.body.contentType())

        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
