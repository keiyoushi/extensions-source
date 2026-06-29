package eu.kanade.tachiyomi.extension.ja.ciaoplus

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

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        if (fragment.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val (seed, version) = when {
            fragment.startsWith("scramble_seed_v2=") -> {
                Pair(fragment.substringAfter("scramble_seed_v2=").toLong(), 2)
            }

            fragment.startsWith("scramble_seed=") -> {
                Pair(fragment.substringAfter("scramble_seed=").toLong(), 1)
            }

            else -> return chain.proceed(request)
        }

        val response = chain.proceed(request)
        val descrambledBody = descrambleImage(response.body, seed, version)

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

    private fun descrambleImage(responseBody: ResponseBody, seed: Long, version: Int): ResponseBody {
        val unscrambledCoords = getUnscrambledCoords(seed)
        val originalBitmap = BitmapFactory.decodeStream(responseBody.byteStream())
            ?: throw Exception("Failed to decode image stream")

        val originalWidth = originalBitmap.width
        val originalHeight = originalBitmap.height

        val descrambledBitmap = Bitmap.createBitmap(originalWidth, originalHeight, originalBitmap.config)
        val canvas = Canvas(descrambledBitmap)

        val (tileWidth, tileHeight) = when (version) {
            2 -> {
                val getTile = { size: Int -> (size / 32) * 8 }
                Pair(getTile(originalWidth), getTile(originalHeight))
            }

            else -> {
                val getTile = { size: Int -> (size / 8 * 8) / 4 }
                Pair(getTile(originalWidth), getTile(originalHeight))
            }
        }

        unscrambledCoords.forEach { coord ->
            val sx = coord.source.x * tileWidth
            val sy = coord.source.y * tileHeight
            val dx = coord.dest.x * tileWidth
            val dy = coord.dest.y * tileHeight

            val srcRect = Rect(sx, sy, sx + tileWidth, sy + tileHeight)
            val destRect = Rect(dx, dy, dx + tileWidth, dy + tileHeight)

            canvas.drawBitmap(originalBitmap, srcRect, destRect, null)
        }

        if (version == 2) {
            val processedWidth = tileWidth * 4
            val processedHeight = tileHeight * 4
            if (originalWidth > processedWidth) {
                val srcRect = Rect(processedWidth, 0, originalWidth, originalHeight)
                val destRect = Rect(processedWidth, 0, originalWidth, originalHeight)
                canvas.drawBitmap(originalBitmap, srcRect, destRect, null)
            }
            if (originalHeight > processedHeight) {
                val srcRect = Rect(0, processedHeight, processedWidth, originalHeight)
                val destRect = Rect(0, processedHeight, processedWidth, originalHeight)
                canvas.drawBitmap(originalBitmap, srcRect, destRect, null)
            }
        }

        originalBitmap.recycle()

        val outputStream = ByteArrayOutputStream()
        descrambledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        descrambledBitmap.recycle()

        return outputStream.toByteArray().toResponseBody("image/jpeg".toMediaType())
    }
}
