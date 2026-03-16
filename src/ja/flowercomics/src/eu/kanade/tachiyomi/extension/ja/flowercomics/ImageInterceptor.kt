package eu.kanade.tachiyomi.extension.ja.flowercomics

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
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (!response.isSuccessful || fragment.isNullOrEmpty() || !fragment.contains(":")) return response

        val parts = fragment.split(":")
        val keyHex = parts[0]
        val ivHex = parts[1]
        val secretKey = SecretKeySpec(keyHex.decodeHex(), "AES")
        val ivSpec = IvParameterSpec(ivHex.decodeHex())
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
