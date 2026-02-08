package eu.kanade.tachiyomi.extension.ja.mangaparkpublisher

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)

        if (!response.isSuccessful) return response

        val encryptedBytes = response.body.bytes()
        val keyBytes = Base64.decode(fragment, Base64.DEFAULT)
        val decryptedBytes = ByteArray(encryptedBytes.size)
        val keyLen = keyBytes.size
        for (i in encryptedBytes.indices) {
            decryptedBytes[i] = (encryptedBytes[i].toInt() xor keyBytes[i % keyLen].toInt()).toByte()
        }

        val body = Buffer().write(decryptedBytes).asResponseBody(response.body.contentType(), decryptedBytes.size.toLong())

        return response.newBuilder()
            .body(body)
            .build()
    }
}
