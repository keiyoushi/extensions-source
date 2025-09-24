package eu.kanade.tachiyomi.extension.zh.onemanhua

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ColaMangaImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.url.fragment?.startsWith(KEY_PREFIX) != true) {
            return response
        }

        val key = request.url.fragment!!.substringAfter(KEY_PREFIX).toByteArray()
        val output = Cipher.getInstance("AES/CBC/PKCS7Padding").let {
            val iv = "0000000000000000".toByteArray()
            it.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            response.body.source().cipherSource(it).buffer()
        }

        return response.newBuilder()
            .body(output.asResponseBody("image/jpeg".toMediaType()))
            .build()
    }

    companion object {
        internal const val KEY_PREFIX = "key="
    }
}
