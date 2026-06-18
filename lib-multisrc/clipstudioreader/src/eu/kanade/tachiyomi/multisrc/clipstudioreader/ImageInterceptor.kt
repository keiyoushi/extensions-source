package eu.kanade.tachiyomi.multisrc.clipstudioreader

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

        if (fragment.isNullOrEmpty() || !fragment.contains("size=") || !response.isSuccessful) {
            return response
        }

        val (arrayStr, scrambleGridW, scrambleGridH) = fragment.substringAfter("size=").split('/', limit = 3)
        val scrambleMapping = arrayStr.split(',').map { it.toInt() }
        val gridW = scrambleGridW.toInt()
        val gridH = scrambleGridH.toInt()

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscrambleImage(bitmap, scrambleMapping, gridW, gridH)
        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun unscrambleImage(
        image: Bitmap,
        scrambleMapping: List<Int>,
        gridWidth: Int,
        gridHeight: Int,
    ): Bitmap {
        val width = image.width
        val height = image.height

        if (scrambleMapping.size < gridWidth * gridHeight || width < 8 * gridWidth || height < 8 * gridHeight) {
            return image
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val pieceWidth = (width / gridWidth) / 8 * 8
        val pieceHeight = (height / gridHeight) / 8 * 8

        val tileAreaWidth = pieceWidth * gridWidth
        val tileAreaHeight = pieceHeight * gridHeight

        val srcRect = Rect()
        val dstRect = Rect()

        for (scrambleIndex in scrambleMapping.indices) {
            val destX = (scrambleIndex % gridWidth) * pieceWidth
            val destY = (scrambleIndex / gridWidth) * pieceHeight
            dstRect.set(destX, destY, destX + pieceWidth, destY + pieceHeight)

            val sourcePieceIndex = scrambleMapping[scrambleIndex]
            val sourceX = (sourcePieceIndex % gridWidth) * pieceWidth
            val sourceY = (sourcePieceIndex / gridWidth) * pieceHeight
            srcRect.set(sourceX, sourceY, sourceX + pieceWidth, sourceY + pieceHeight)

            canvas.drawBitmap(image, srcRect, dstRect, null)
        }

        if (tileAreaWidth < width) {
            srcRect.set(tileAreaWidth, 0, width, height)
            canvas.drawBitmap(image, srcRect, srcRect, null)
        }
        if (tileAreaHeight < height) {
            srcRect.set(0, tileAreaHeight, tileAreaWidth, height)
            canvas.drawBitmap(image, srcRect, srcRect, null)
        }

        return result
    }

    companion object {
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
