package eu.kanade.tachiyomi.extension.ja.drecomics

import android.util.Base64
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (!response.isSuccessful && request.url.host == "cdn.drecomi-plus.jp") {
            throw IOException("This service can only be used from Japan.")
        }

        if (fragment.isNullOrEmpty() || !fragment.contains(":")) return response

        val (key, iv) = fragment.split(":")
        val keyBytes = Base64.decode(key, Base64.DEFAULT)
        val ivBytes = Base64.decode(iv, Base64.DEFAULT)
        return response.aesDecrypt(aesCbc(keyBytes, ivBytes))
    }
}
