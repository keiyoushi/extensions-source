package eu.kanade.tachiyomi.extension.ja.piccoma

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import eu.kanade.tachiyomi.lib.seedrandom.SeedRandom
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import kotlin.math.ceil

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        val isScrambled = url.fragment == "scrambled"
        if (!isScrambled) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)
        val body = response.body.source()
        val bitmap = BitmapFactory.decodeStream(body.inputStream())

        val seed = extractSeed(url)
        val result = unscrambleImg(bitmap, seed, bitmap.width, bitmap.height)
        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody("image/jpeg".toMediaType(), buffer.size))
            .build()
    }

    private fun extractSeed(url: HttpUrl): String {
        val checksum = url.pathSegments.let { it.getOrNull(it.size - 2) }
        val expiration = url.queryParameter("expires")!!

        var sum = 0
        for (char in expiration) {
            sum += char.digitToInt()
        }

        val residualIndex = sum % checksum!!.length
        val rotated = checksum.takeLast(residualIndex) + checksum.dropLast(residualIndex)

        return dd(rotated)
    }

    // Example
    // originalHash: "GB0G[TQ3FPT7ECYJCSONTG"
    // rotatedSeed:  "SONTGGB0G[TQ3FPT7ECYJC"
    // finalKey:     "RNOTGGC1GZTQ3GQT6ECYKB"
    // If character changed, the bit is 1. If it's the same, the bit is 0.
    // 001100010110001011000111
    // WASM bitmask 0x3162C7 = 3236551
    private fun dd(s: String): String {
        val mask = 3236551
        val bytes = s.toByteArray(Charsets.UTF_8)
        for (i in bytes.indices) {
            if ((mask shr i) and 1 == 1) {
                val b = bytes[i].toInt()
                bytes[i] = (b + 1 - ((b shl 1) and 2)).toByte()
            }
        }
        return String(bytes, Charsets.UTF_8)
    }

    private class Point(val x: Int, val y: Int)

    private fun unscrambleImg(bitmap: Bitmap, seed: String, width: Int, height: Int): Bitmap {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val tileSize = 50
        val tileGroups = mutableMapOf<String, MutableList<Point>>()

        val columns = ceil(width.toDouble() / tileSize).toInt()
        val rows = ceil(height.toDouble() / tileSize).toInt()
        val tileCount = columns * rows

        for (index in 0 until tileCount) {
            val row = index / columns
            val col = index % columns
            val x = col * tileSize
            val y = row * tileSize
            val w = if (x + tileSize > width) width - x else tileSize
            val h = if (y + tileSize > height) height - y else tileSize

            val key = "$w-$h"
            val list = tileGroups.getOrPut(key) { mutableListOf() }
            list.add(Point(x, y))
        }

        for ((_, tiles) in tileGroups) {
            val rng = SeedRandom(seed)

            val shuffledTiles = rng.shuffle(tiles.toMutableList())

            for (i in tiles.indices) {
                val dest = tiles[i]
                val src = shuffledTiles[i]

                val w = if (dest.x + tileSize > width) width - dest.x else tileSize
                val h = if (dest.y + tileSize > height) height - dest.y else tileSize

                val srcRect = Rect(src.x, src.y, src.x + w, src.y + h)
                val destRect = Rect(dest.x, dest.y, dest.x + w, dest.y + h)

                canvas.drawBitmap(bitmap, srcRect, destRect, null)
            }
        }
        return result
    }
}
