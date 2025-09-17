package eu.kanade.tachiyomi.extension.en.azuki

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class ImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.queryParameterNames.contains("drm")) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)
        if (!response.isSuccessful) {
            return response
        }

        val encryptedBytes = response.body.bytes()
        val decryptedBytes = decryptImage(encryptedBytes)
        val decryptedBody = decryptedBytes.toResponseBody(response.body.contentType())

        return response.newBuilder()
            .body(decryptedBody)
            .build()
    }

// https://www.azuki.co/assets/js/DecryptedImage.57631a1f.js
    private fun decryptImage(encryptedData: ByteArray): ByteArray {
        val keyByte = 174
        return encryptedData.map { (it.toInt() xor keyByte).toByte() }.toByteArray()
    }
}
