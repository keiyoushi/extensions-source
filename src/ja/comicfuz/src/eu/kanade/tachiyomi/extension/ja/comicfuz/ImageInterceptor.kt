package eu.kanade.tachiyomi.extension.ja.comicfuz

import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.decodeHex
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response

object ImageInterceptor : Interceptor {
    private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        val key = url.queryParameter("key")
            ?: return chain.proceed(chain.request())
        val iv = url.queryParameter("iv")!!

        val cleanRequest = chain.request().newBuilder()
            .url(
                url.newBuilder()
                    .removeAllQueryParameters("key")
                    .removeAllQueryParameters("iv")
                    .build(),
            )
            .build()

        val response = chain.proceed(cleanRequest)
        if (!response.isSuccessful) return response

        return response.aesDecrypt(aesCbc(key.decodeHex(), iv.decodeHex()), JPEG_MEDIA_TYPE)
    }
}
