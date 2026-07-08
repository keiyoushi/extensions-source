package eu.kanade.tachiyomi.extension.ja.coronaex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val hash = request.url.queryParameter("drm_hash") ?: return response

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscramble(bitmap, hash)
        bitmap.recycle()

        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun unscramble(image: Bitmap, drmHash: String): Bitmap {
        val scrambleData = Base64.decode(drmHash, Base64.DEFAULT)
        val col = scrambleData[0].toInt() and 0xFF
        val row = scrambleData[1].toInt() and 0xFF

        val width = image.width
        val height = image.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val blockWidth = (width - width % 8) / col
        val blockHeight = (height - height % 8) / row
        val blockAreaWidth = blockWidth * col
        val blockAreaHeight = blockHeight * row
        val srcRect = Rect()
        val dstRect = Rect()

        for (dstBlockIndex in 0 until (col * row)) {
            val srcBlockIndex = scrambleData[dstBlockIndex + 2].toInt() and 0xFF
            val srcX = (srcBlockIndex % col) * blockWidth
            val srcY = (srcBlockIndex / col) * blockHeight
            val dstX = (dstBlockIndex % col) * blockWidth
            val dstY = (dstBlockIndex / col) * blockHeight

            srcRect.set(srcX, srcY, srcX + blockWidth, srcY + blockHeight)
            dstRect.set(dstX, dstY, dstX + blockWidth, dstY + blockHeight)

            canvas.drawBitmap(image, srcRect, dstRect, null)
        }

        if (blockAreaWidth < width) {
            srcRect.set(blockAreaWidth, 0, width, height)
            canvas.drawBitmap(image, srcRect, srcRect, null)
        }
        if (blockAreaHeight < height) {
            srcRect.set(0, blockAreaHeight, blockAreaWidth, height)
            canvas.drawBitmap(image, srcRect, srcRect, null)
        }

        return result
    }

    companion object {
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
