package eu.kanade.tachiyomi.extension.en.jnovel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url
        val fragment = url.fragment

        if (!fragment.isNullOrEmpty() || !url.pathSegments.contains("XEBP") || !response.isSuccessful) {
            return response
        }

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscramble(/*TODO()*/)
        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.WEBP, 100, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun unscramble() {
        TODO()
    }

    companion object {
        private val MEDIA_TYPE = "image/webp".toMediaType()
    }
}
