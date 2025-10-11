package eu.kanade.tachiyomi.multisrc.comiciviewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class ImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val scrambleData = request.url.queryParameter("scramble")

        if (scrambleData.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val newUrl = request.url.newBuilder()
            .removeAllQueryParameters("scramble")
            .build()
        val newRequest = request.newBuilder().url(newUrl).build()

        val response = chain.proceed(newRequest)

        if (!response.isSuccessful) {
            return response
        }

        val tiles = buildList {
            scrambleData.drop(1).dropLast(1).replace(" ", "").split(",").forEach {
                val scrambleInt = it.toInt()
                add(TilePos(scrambleInt / 4, scrambleInt % 4))
            }
        }

        val scrambledImg = BitmapFactory.decodeStream(response.body.byteStream())
        val descrambledImg =
            unscrambleImage(scrambledImg, scrambledImg.width, scrambledImg.height, tiles)

        val output = ByteArrayOutputStream()
        descrambledImg.compress(Bitmap.CompressFormat.JPEG, 90, output)

        val body = output.toByteArray().toResponseBody("image/jpeg".toMediaType())

        return response.newBuilder().body(body).build()
    }

    private fun unscrambleImage(
        rawImage: Bitmap,
        width: Int,
        height: Int,
        tiles: List<TilePos>,
    ): Bitmap {
        val descrambledImg = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(descrambledImg)

        val tileWidth = width / 4
        val tileHeight = height / 4

        var count = 0
        for (x in 0..3) {
            for (y in 0..3) {
                val desRect = Rect(
                    x * tileWidth,
                    y * tileHeight,
                    (x + 1) * tileWidth,
                    (y + 1) * tileHeight,
                )
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
