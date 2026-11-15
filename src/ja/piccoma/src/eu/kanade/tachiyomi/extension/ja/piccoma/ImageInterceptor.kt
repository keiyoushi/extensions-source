package eu.kanade.tachiyomi.extension.ja.piccoma

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import keiyoushi.utils.SeedRandom
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url
        val fragment = url.fragment

        if (fragment.isNullOrEmpty() || fragment != "scrambled" || !response.isSuccessful) return response

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val seed = extractSeed(url)
        val result = unscrambleImg(bitmap, seed)
        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun extractSeed(url: HttpUrl): String {
        val checksum = url.pathSegments[3]
        val expiration = url.queryParameter("expires")!!

        val sum = expiration.sumOf { it.digitToInt() }
        val residualIndex = sum % checksum.length
        val rotated = checksum.takeLast(residualIndex) + checksum.dropLast(residualIndex)

        return rotated.dd()
    }

    // Example
    // originalHash: "GB0G[TQ3FPT7ECYJCSONTG"
    // rotatedSeed:  "SONTGGB0G[TQ3FPT7ECYJC"
    // finalKey:     "RNOTGGC1GZTQ3GQT6ECYKB"
    // If character changed, the bit is 1. If it's the same, the bit is 0.
    // 001100010110001011000111
    // WASM bitmask 0x3162C7 = 3236551
    private fun String.dd(): String {
        val bytes = this.toByteArray(Charsets.UTF_8)
        for (i in bytes.indices) {
            if ((BITMASK shr i) and 1 == 1) {
                bytes[i] = (bytes[i].toInt() xor 1).toByte()
            }
        }
        return String(bytes, Charsets.UTF_8)
    }

    private class Point(val x: Int, val y: Int)

    private fun unscrambleImg(bitmap: Bitmap, seed: String): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val columns = (width + TILE_SIZE - 1) / TILE_SIZE
        val rows = (height + TILE_SIZE - 1) / TILE_SIZE
        val tileGroups = mutableMapOf<Pair<Int, Int>, MutableList<Point>>()

        for (index in 0 until columns * rows) {
            val x = (index % columns) * TILE_SIZE
            val y = (index / columns) * TILE_SIZE
            val w = if (x + TILE_SIZE > width) width - x else TILE_SIZE
            val h = if (y + TILE_SIZE > height) height - y else TILE_SIZE
            tileGroups.getOrPut(w to h) { mutableListOf() }.add(Point(x, y))
        }

        val srcRect = Rect()
        val destRect = Rect()

        for ((size, tiles) in tileGroups) {
            val (w, h) = size
            val shuffled = SeedRandom(seed).shuffle(tiles)

            for (i in tiles.indices) {
                val dest = tiles[i]
                val src = shuffled[i]
                srcRect.set(src.x, src.y, src.x + w, src.y + h)
                destRect.set(dest.x, dest.y, dest.x + w, dest.y + h)
                canvas.drawBitmap(bitmap, srcRect, destRect, null)
            }
        }
        return result
    }

    companion object {
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
        private const val TILE_SIZE = 50
        private const val BITMASK = 3236551
    }
}
