package eu.kanade.tachiyomi.extension.ja.comicfuz

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object ImageInterceptor : Interceptor {
    private val mediaType = "image/jpeg".toMediaType()

    private inline val AES: Cipher
        get() = Cipher.getInstance("AES/CBC/PKCS7Padding")

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        val key = url.queryParameter("key")
            ?: return chain.proceed(chain.request())
        val iv = url.queryParameter("iv")!!

        val response = chain.proceed(
            chain.request().newBuilder().url(
                url.newBuilder()
                    .removeAllQueryParameters("key")
                    .removeAllQueryParameters("iv")
                    .build(),
            ).build(),
        )

        val body = response.body.bytes()
            .decode(key.decodeHex(), iv.decodeHex())

        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun ByteArray.decode(key: ByteArray, iv: ByteArray) = AES.let {
        it.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        it.doFinal(this).toResponseBody(mediaType)
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
