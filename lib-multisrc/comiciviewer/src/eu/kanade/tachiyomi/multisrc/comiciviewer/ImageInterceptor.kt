package eu.kanade.tachiyomi.multisrc.comiciviewer

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
        val scrambleData = request.url.fragment

        if (scrambleData.isNullOrEmpty() || !scrambleData.startsWith("scramble=") || !response.isSuccessful) return response

        val tiles = scrambleData.substringAfter("scramble=").trim('[', ']').split(",").map { it.trim().toInt() }
        val scrambledImg = BitmapFactory.decodeStream(response.body.byteStream())
        val descrambledImg = unscrambleImage(scrambledImg, tiles)
        scrambledImg.recycle()
        val buffer = Buffer()
        descrambledImg.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        descrambledImg.recycle()
        val body = buffer.asResponseBody(MEDIA_TYPE, buffer.size)

        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun unscrambleImage(image: Bitmap, tiles: List<Int>): Bitmap {
        val width = image.width
        val height = image.height

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val tileWidth = width / GRID_SIZE
        val tileHeight = height / GRID_SIZE
        val tileAreaWidth = tileWidth * GRID_SIZE
        val tileAreaHeight = tileHeight * GRID_SIZE

        val srcRect = Rect()
        val dstRect = Rect()

        for (destIndex in tiles.indices) {
            val destX = (destIndex / GRID_SIZE) * tileWidth
            val destY = (destIndex % GRID_SIZE) * tileHeight
            dstRect.set(destX, destY, destX + tileWidth, destY + tileHeight)

            val sourceIndex = tiles[destIndex]
            val sourceX = (sourceIndex / GRID_SIZE) * tileWidth
            val sourceY = (sourceIndex % GRID_SIZE) * tileHeight
            srcRect.set(sourceX, sourceY, sourceX + tileWidth, sourceY + tileHeight)

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
        private const val GRID_SIZE = 4
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
