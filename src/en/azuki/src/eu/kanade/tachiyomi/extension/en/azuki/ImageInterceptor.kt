package eu.kanade.tachiyomi.extension.en.azuki

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isSuccessful || !request.url.queryParameterNames.contains("drm")) {
            return response
        }

        val bytes = response.body.bytes()

        // https://www.azuki.co/assets/js/DecryptedImage.57631a1f.js
        for (i in bytes.indices) {
            bytes[i] = (bytes[i].toInt() xor 174).toByte()
        }

        val buffer = Buffer().write(bytes)
        val body = buffer.asResponseBody(response.body.contentType(), buffer.size)

        return response.newBuilder()
            .body(body)
            .build()
    }
}
