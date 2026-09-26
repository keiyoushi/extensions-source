package eu.kanade.tachiyomi.extension.ja.drecomics

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.IOException
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

        if (!response.isSuccessful && request.url.host == "cdn.drecomi-plus.jp") {
            throw IOException("This service can only be used from Japan.")
        }

        if (fragment.isNullOrEmpty() || !fragment.contains(":")) return response

        val (key, iv) = fragment.split(":")
        val secretKey = SecretKeySpec(Base64.decode(key, Base64.DEFAULT), "AES")
        val ivSpec = IvParameterSpec(Base64.decode(iv, Base64.DEFAULT))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        val body = response.body.source().cipherSource(cipher).buffer().asResponseBody(response.body.contentType())

        return response.newBuilder()
            .body(body)
            .build()
    }
}
