package eu.kanade.tachiyomi.extension.en.emaqi

import keiyoushi.utils.decodeHex
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment == null || !response.isSuccessful) return response

        val secretKey = SecretKeySpec(fragment.decodeHex(), "AES")
        val source = response.body.source()
        val cipher: Cipher
        if (source.peek().readByte().toInt() == 2) {
            source.skip(2)
            cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, source.readByteArray(16)))
        } else {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(source.readByteArray(16)))
        }

        val body = source.cipherSource(cipher).buffer().asResponseBody(response.body.contentType())
        return response.newBuilder()
            .body(body)
            .build()
    }
}
