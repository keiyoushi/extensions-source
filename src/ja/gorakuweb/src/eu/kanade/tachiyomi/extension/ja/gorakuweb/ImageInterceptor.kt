package eu.kanade.tachiyomi.extension.ja.gorakuweb

import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.decodeHex
import okhttp3.Interceptor
import okhttp3.Response

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty() || !fragment.contains(":") || !response.isSuccessful) return response

        val (key, iv) = fragment.split(":")
        return response.aesDecrypt(aesCbc(key.decodeHex(), iv.decodeHex()))
    }
}
