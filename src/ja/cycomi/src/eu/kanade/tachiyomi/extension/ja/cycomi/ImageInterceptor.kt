package eu.kanade.tachiyomi.extension.ja.cycomi

import keiyoushi.utils.rc4
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer

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

        val source = response.body.source()
        val body = source.rc4(key.toByteArray()).buffer().asResponseBody(response.body.contentType())
        return response.newBuilder()
            .body(body)
            .build()
    }
}
