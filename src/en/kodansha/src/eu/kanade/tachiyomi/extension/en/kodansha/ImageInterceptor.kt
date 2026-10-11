package eu.kanade.tachiyomi.extension.en.kodansha

import keiyoushi.lib.xorinterceptor.xor
import okhttp3.Interceptor
import okhttp3.Response

// Chapter page images are XOR-obfuscated by the Azuki reader
class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful || chain.request().url.host != CONTENT_HOST) {
            return response
        }
        return response.xor(KEY)
    }

    companion object {
        private const val CONTENT_HOST = "production.image-content.azuki.co"
        private const val KEY = 174.toByte()
    }
}
