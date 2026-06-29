package eu.kanade.tachiyomi.extension.ja.cycomi

import keiyoushi.utils.rc4
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.url.fragment != "decrypt") {
            return response
        }

        val key = request.url.pathSegments[3]
        if (key.contains("end_page")) {
            return response
        }

        val decrypted = rc4(key.toByteArray(), response.body.bytes())
        val buffer = Buffer().write(decrypted)
        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    companion object {
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
