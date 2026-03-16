package eu.kanade.tachiyomi.extension.en.kmanga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

// https://greasyfork.org/en/scripts/467901-k-manga-ripper
class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (!response.isSuccessful || fragment.isNullOrEmpty() || !fragment.startsWith("scramble_seed=")) {
            return response
        }

        val seed = fragment.substringAfter("scramble_seed=").toLong()
        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscramble(bitmap, seed)

        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()
        val body = buffer.asResponseBody("image/jpeg".toMediaType(), buffer.size)

        return response.newBuilder()
            .body(body)
            .build()
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

    private fun unscramble(image: Bitmap, seed: Long): Bitmap {
        val width = image.width
        val height = image.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val blockWidth = (width / 8 * 8) / 4
        val blockHeight = (height / 8 * 8) / 4
        val srcRect = Rect()
        val dstRect = Rect()

        val unscrambledCoords = getUnscrambledCoords(seed)

        unscrambledCoords.forEach {
            val srcX = it.source.x * blockWidth
            val srcY = it.source.y * blockHeight
            val dstX = it.dest.x * blockWidth
            val dstY = it.dest.y * blockHeight

            srcRect.set(srcX, srcY, srcX + blockWidth, srcY + blockHeight)
            dstRect.set(dstX, dstY, dstX + blockWidth, dstY + blockHeight)

            canvas.drawBitmap(image, srcRect, dstRect, null)
        }
        return result
    }
}
