package eu.kanade.tachiyomi.extension.en.omoi

import keiyoushi.lib.xorinterceptor.xor
import okhttp3.Interceptor
import okhttp3.Response

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isSuccessful || !request.url.queryParameterNames.contains("drm")) {
            return response
        }
        return response.xor(KEY)
    }

    companion object {
        private const val KEY = 174.toByte()
    }
}
