package eu.kanade.tachiyomi.extension.en.emaqi

import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesGcm
import keiyoushi.utils.decodeHex
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment == null || !response.isSuccessful) return response

        val key = fragment.decodeHex()
        val source = response.body.source()
        val isGcm = source.peek().readByte().toInt() == 2
        if (isGcm) source.skip(2)
        val iv = source.readByteArray(16)
        val cipher = if (isGcm) aesGcm(key, iv) else aesCbc(key, iv)

        val body = source.cipherSource(cipher).buffer().asResponseBody(response.body.contentType())
        return response.newBuilder()
            .body(body)
            .build()
    }
}
