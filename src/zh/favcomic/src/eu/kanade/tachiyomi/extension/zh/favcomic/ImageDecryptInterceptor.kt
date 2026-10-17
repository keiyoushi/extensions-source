package eu.kanade.tachiyomi.extension.zh.favcomic

import android.util.Base64
import keiyoushi.utils.aesDecryptWithPrependedIv
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response

class ImageDecryptInterceptor : Interceptor {

    private val keyBytes = Base64.decode("NlgrYjYuRT5ic1hifSs9Tg==", Base64.DEFAULT)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isSuccessful || request.url.fragment != "true") {
            return response
        }

        val contentType = getMimeType(request.url.toString()).toMediaType()
        return response.aesDecryptWithPrependedIv(
            key = keyBytes,
            ivSize = 16,
            contentType = contentType,
        )
    }

    private fun getMimeType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".gif") -> "image/gif"
            else -> "application/octet-stream"
        }
    }
}
