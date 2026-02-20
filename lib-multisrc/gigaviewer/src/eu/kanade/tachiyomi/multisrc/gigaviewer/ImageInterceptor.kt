package eu.kanade.tachiyomi.multisrc.gigaviewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import kotlin.math.floor

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty() || fragment != "scramble") {
            return response
        }

        if (!response.isSuccessful) {
            return response
        }

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscramble(bitmap)

        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()
        val body = buffer.asResponseBody("image/jpeg".toMediaType(), buffer.size)

        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun unscramble(image: Bitmap): Bitmap {
        val width = image.width
        val height = image.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val blockWidth = (floor(width.toDouble() / (DIVIDE_NUM * MULTIPLE_NUM)) * MULTIPLE_NUM).toInt()
        val blockHeight = (floor(height.toDouble() / (DIVIDE_NUM * MULTIPLE_NUM)) * MULTIPLE_NUM).toInt()
        val srcRect = Rect()
        val dstRect = Rect()

        canvas.drawBitmap(image, 0f, 0f, null)

        for (e in 0 until DIVIDE_NUM * DIVIDE_NUM) {
            val dstBlockIndex = (e % DIVIDE_NUM) * DIVIDE_NUM + (e / DIVIDE_NUM)
            val srcX = (e % DIVIDE_NUM) * blockWidth
            val srcY = (e / DIVIDE_NUM) * blockHeight
            val dstX = (dstBlockIndex % DIVIDE_NUM) * blockWidth
            val dstY = (dstBlockIndex / DIVIDE_NUM) * blockHeight

            srcRect.set(srcX, srcY, srcX + blockWidth, srcY + blockHeight)
            dstRect.set(dstX, dstY, dstX + blockWidth, dstY + blockHeight)

            canvas.drawBitmap(image, srcRect, dstRect, null)
        }

        return result
    }

    companion object {
        private const val DIVIDE_NUM = 4
        private const val MULTIPLE_NUM = 8
    }
}
