package keiyoushi.lib.clipstudioreader

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

        val pieceWidth = 8 * floor(floor(width.toFloat() / gridWidth) / 8).toInt()
        val pieceHeight = 8 * floor(floor(height.toFloat() / gridHeight) / 8).toInt()

        val srcRect = Rect()
        val dstRect = Rect()

        for (scrambleIndex in scrambleMapping.indices) {
            val destX = scrambleIndex % gridWidth * pieceWidth
            val destY = floor(scrambleIndex.toFloat() / gridWidth).toInt() * pieceHeight
            dstRect.set(destX, destY, destX + pieceWidth, destY + pieceHeight)

            val sourcePieceIndex = scrambleMapping[scrambleIndex]
            val sourceX = sourcePieceIndex % gridWidth * pieceWidth
            val sourceY = floor(sourcePieceIndex.toFloat() / gridWidth).toInt() * pieceHeight
            srcRect.set(sourceX, sourceY, sourceX + pieceWidth, sourceY + pieceHeight)

            canvas.drawBitmap(image, srcRect, dstRect, null)
        }
        return result
    }

    companion object {
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
