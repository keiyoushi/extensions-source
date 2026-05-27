package eu.kanade.tachiyomi.extension.ja.sokuyomi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty() || fragment != "scramble" || !response.isSuccessful) return response

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscramble(bitmap)
        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.WEBP, 100, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun unscramble(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val tileW = width / GRID
        val tileH = height / GRID

        val srcRect = Rect()
        val dstRect = Rect()

        for (row in 0 until GRID) {
            for (col in 0 until GRID) {
                srcRect.set(col * tileW, row * tileH, (col + 1) * tileW, (row + 1) * tileH)
                dstRect.set(row * tileW, col * tileH, (row + 1) * tileW, (col + 1) * tileH)
                canvas.drawBitmap(bitmap, srcRect, dstRect, null)
            }
        }

        return result
    }

    companion object {
        private val MEDIA_TYPE = "image/webp".toMediaType()
        private const val GRID = 4
    }
}
