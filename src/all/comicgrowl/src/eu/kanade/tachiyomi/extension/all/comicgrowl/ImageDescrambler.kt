package eu.kanade.tachiyomi.extension.all.comicgrowl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

object ImageDescrambler {

    // Left-top corner position
    private class TilePos(val x: Int, val y: Int)

    /**
     * Interceptor to descramble the image.
     */
    fun interceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val scramble = request.url.fragment ?: return response // return if no scramble fragment
        val tiles = buildList {
            scramble.split("-").forEachIndexed { index, s ->
                val scrambleInt = s.toInt()
                add(index, TilePos(scrambleInt / 4, scrambleInt % 4))
            }
        }

        val scrambledImg = BitmapFactory.decodeStream(response.body.byteStream())
        val descrambledImg = drawDescrambledImage(scrambledImg, scrambledImg.width, scrambledImg.height, tiles)

        val output = ByteArrayOutputStream()
        descrambledImg.compress(Bitmap.CompressFormat.JPEG, 90, output)

        val body = output.toByteArray().toResponseBody("image/jpeg".toMediaType())

        return response.newBuilder().body(body).build()
    }

    private fun drawDescrambledImage(rawImage: Bitmap, width: Int, height: Int, tiles: List<TilePos>): Bitmap {
        // Prepare canvas
        val descrambledImg = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(descrambledImg)

        // Tile width and height(4x4)
        val tileWidth = width / 4
        val tileHeight = height / 4

        // Draw rect
        var count = 0
        for (x in 0..3) {
            for (y in 0..3) {
                val desRect = Rect(x * tileWidth, y * tileHeight, (x + 1) * tileWidth, (y + 1) * tileHeight)
                val srcRect = Rect(
                    tiles[count].x * tileWidth,
                    tiles[count].y * tileHeight,
                    (tiles[count].x + 1) * tileWidth,
                    (tiles[count].y + 1) * tileHeight,
                )
                canvas.drawBitmap(rawImage, srcRect, desRect, null)
                count++
            }
        }
        return descrambledImg
    }
}
