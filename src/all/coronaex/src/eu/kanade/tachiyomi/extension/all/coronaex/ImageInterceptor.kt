package eu.kanade.tachiyomi.extension.all.coronaex

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

        if (request.url.fragment.isNullOrEmpty() && !request.url.queryParameterNames.contains("drm_hash")) {
            return response
        }

        val hash = request.url.fragment
        val imageBytes = response.body.bytes()
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val result = unscramble(bitmap, hash)

        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody("image/jpeg".toMediaType(), buffer.size))
            .build()
    }

    private fun unscramble(image: Bitmap, drmHash: String?): Bitmap {
        val scrambleData = Base64.decode(drmHash, Base64.DEFAULT)
        val col = scrambleData[0].toInt() and 0xFF
        val row = scrambleData[1].toInt() and 0xFF
        val blockIndexMap = scrambleData.drop(2)

        val width = image.width
        val height = image.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val blockWidth = (width - width % 8) / col
        val blockHeight = (height - height % 8) / row
        val srcRect = Rect()
        val dstRect = Rect()

        canvas.drawBitmap(image, 0f, 0f, null)

        for (dstBlockIndex in 0 until (col * row)) {
            val srcBlockIndex = blockIndexMap[dstBlockIndex].toInt() and 0xFF
            val srcX = (srcBlockIndex % col) * blockWidth
            val srcY = (srcBlockIndex / col) * blockHeight
            val dstX = (dstBlockIndex % col) * blockWidth
            val dstY = (dstBlockIndex / col) * blockHeight

            srcRect.set(srcX, srcY, srcX + blockWidth, srcY + blockHeight)
            dstRect.set(dstX, dstY, dstX + blockWidth, dstY + blockHeight)

            canvas.drawBitmap(image, srcRect, dstRect, null)
        }

        return result
    }
}
