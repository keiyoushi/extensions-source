package eu.kanade.tachiyomi.extension.en.kmanga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

// https://greasyfork.org/en/scripts/467901-k-manga-ripper
class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        if (fragment.isNullOrEmpty() || !fragment.startsWith("scramble_seed=")) {
            return chain.proceed(request)
        }

        val seed = fragment.substringAfter("scramble_seed=").toLong()
        val response = chain.proceed(request)
        val descrambledBody = descrambleImage(response.body, seed)

        return response.newBuilder().body(descrambledBody).build()
    }

    private class Coord(val x: Int, val y: Int)
    private class CoordPair(val source: Coord, val dest: Coord)

    private fun xorshift32(seed: UInt): UInt {
        var n = seed
        n = n xor (n shl 13)
        n = n xor (n shr 17)
        n = n xor (n shl 5)
        return n
    }

    private fun getUnscrambledCoords(seed: Long): List<CoordPair> {
        var seed32 = seed.toUInt()
        val pairs = mutableListOf<Pair<UInt, Int>>()

        for (i in 0 until 16) {
            seed32 = xorshift32(seed32)
            pairs.add(seed32 to i)
        }

        pairs.sortBy { it.first }
        val sortedVal = pairs.map { it.second }

        return sortedVal.mapIndexed { i, e ->
            CoordPair(
                source = Coord(x = e % 4, y = e / 4),
                dest = Coord(x = i % 4, y = i / 4),
            )
        }
    }

    private fun descrambleImage(responseBody: ResponseBody, seed: Long): ResponseBody {
        val unscrambledCoords = getUnscrambledCoords(seed)
        val originalBitmap = BitmapFactory.decodeStream(responseBody.byteStream())
            ?: throw Exception("Failed to decode image stream")

        val originalWidth = originalBitmap.width
        val originalHeight = originalBitmap.height

        val descrambledBitmap = Bitmap.createBitmap(originalWidth, originalHeight, originalBitmap.config)
        val canvas = Canvas(descrambledBitmap)

        val getTileDimension = { size: Int -> (size / 8 * 8) / 4 }
        val tileWidth = getTileDimension(originalWidth)
        val tileHeight = getTileDimension(originalHeight)

        unscrambledCoords.forEach { coord ->
            val sx = coord.source.x * tileWidth
            val sy = coord.source.y * tileHeight
            val dx = coord.dest.x * tileWidth
            val dy = coord.dest.y * tileHeight

            val srcRect = Rect(sx, sy, sx + tileWidth, sy + tileHeight)
            val destRect = Rect(dx, dy, dx + tileWidth, dy + tileHeight)

            canvas.drawBitmap(originalBitmap, srcRect, destRect, null)
        }
        originalBitmap.recycle()

        val outputStream = ByteArrayOutputStream()
        descrambledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        descrambledBitmap.recycle()

        return outputStream.toByteArray().toResponseBody("image/jpeg".toMediaType())
    }
}
