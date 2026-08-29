package eu.kanade.tachiyomi.extension.ja.ciaoplus

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

        if (fragment.isNullOrEmpty() || !response.isSuccessful) return response

        val (seed, version) = when {
            fragment.startsWith("scramble_seed_v2=") ->
                fragment.substringAfter("scramble_seed_v2=").toLong() to 2
            fragment.startsWith("scramble_seed=") ->
                fragment.substringAfter("scramble_seed=").toLong() to 1
            else -> return response
        }

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = descramble(bitmap, seed, version)
        bitmap.recycle()

        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    private class Coord(val x: Int, val y: Int)

    private class CoordPair(val source: Coord, val dest: Coord)

    private fun UInt.xorshift32(): UInt {
        var n = this
        n = n xor (n shl 13)
        n = n xor (n shr 17)
        n = n xor (n shl 5)
        return n
    }

    private fun getUnscrambledCoords(seed: Long): List<CoordPair> {
        var seed32 = seed.toUInt()
        val pairs = mutableListOf<Pair<UInt, Int>>()

        for (i in 0 until 16) {
            seed32 = seed32.xorshift32()
            pairs.add(seed32 to i)
        }

        pairs.sortBy { it.first }

        return pairs.mapIndexed { destIndex, (_, sourceIndex) ->
            CoordPair(
                source = Coord(x = sourceIndex % GRID_SIZE, y = sourceIndex / GRID_SIZE),
                dest = Coord(x = destIndex % GRID_SIZE, y = destIndex / GRID_SIZE),
            )
        }
    }

    private fun Int.tileSizeV1() = (this / 8 * 8) / 4

    private fun Int.tileSizeV2() = (this / 32) * 8

    private fun descramble(image: Bitmap, seed: Long, version: Int): Bitmap {
        val width = image.width
        val height = image.height

        val (tileWidth, tileHeight) = if (version == 2) {
            width.tileSizeV2() to height.tileSizeV2()
        } else {
            width.tileSizeV1() to height.tileSizeV1()
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val srcRect = Rect()
        val dstRect = Rect()

        for (coord in getUnscrambledCoords(seed)) {
            val srcX = coord.source.x * tileWidth
            val srcY = coord.source.y * tileHeight
            val dstX = coord.dest.x * tileWidth
            val dstY = coord.dest.y * tileHeight

            srcRect.set(srcX, srcY, srcX + tileWidth, srcY + tileHeight)
            dstRect.set(dstX, dstY, dstX + tileWidth, dstY + tileHeight)

            canvas.drawBitmap(image, srcRect, dstRect, null)
        }

        val tileAreaWidth = tileWidth * GRID_SIZE
        val tileAreaHeight = tileHeight * GRID_SIZE

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
        private const val GRID_SIZE = 4
    }
}
