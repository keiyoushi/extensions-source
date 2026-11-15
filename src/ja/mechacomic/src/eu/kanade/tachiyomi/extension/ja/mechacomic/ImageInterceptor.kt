package eu.kanade.tachiyomi.extension.ja.mechacomic

import keiyoushi.utils.aesDecryptWithPrependedIv
import keiyoushi.utils.decodeHex
import okhttp3.Interceptor
import okhttp3.Response

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment == null || !fragment.startsWith("key=") || !response.isSuccessful) return response

        // The IV is the first 16 bytes of the image body, not part of the key.
        val key = fragment.substringAfter("key=")
        return response.aesDecryptWithPrependedIv(key.decodeHex(), 16)
    }
}
