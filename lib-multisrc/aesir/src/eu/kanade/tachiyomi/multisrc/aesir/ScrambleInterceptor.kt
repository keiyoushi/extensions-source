package eu.kanade.tachiyomi.multisrc.aesir

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException

/** Puts back the tiles of "drm" pages; the page's decrypted scramble seed comes in the URL fragment. */
class ScrambleInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val seed = request.url.fragment ?: return chain.proceed(request)

        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val scrambled = response.use { BitmapFactory.decodeStream(it.body.byteStream()) }
            ?: throw IOException("Could not decode scrambled image")
        val unscrambled = AutoCloseable { scrambled.recycle() }.use { descramble(scrambled, seed) }

        val buffer = Buffer()
        AutoCloseable { unscrambled.recycle() }.use {
            if (!unscrambled.compress(Bitmap.CompressFormat.JPEG, 95, buffer.outputStream())) {
                throw IOException("Could not encode descrambled image")
            }
        }

        return response.newBuilder()
            .header("Content-Type", JPEG_MEDIA_TYPE.toString())
            .header("Content-Length", buffer.size.toString())
            .body(buffer.asResponseBody(JPEG_MEDIA_TYPE, buffer.size))
            .build()
    }

    // Port of the site reader's descrambleBuffer: Fisher-Yates over the tile grid with mulberry32
    private fun descramble(image: Bitmap, seed: String): Bitmap {
        val width = image.width
        val height = image.height
        val cols = (width / 175).coerceAtLeast(2).coerceAtMost(width)
        val rows = (height / 250).coerceAtLeast(4).coerceAtMost(height)
        val tileWidth = (width / cols).coerceAtLeast(1)
        val tileHeight = (height / rows).coerceAtLeast(1)

        val hash = seed.encodeToByteArray().fold(0) { hash, byte -> (byte.toInt() and 0xFF) + hash * 31 }
        var state = if (hash == 0) {
            123456789
        } else if (hash < 0) {
            -hash
        } else {
            hash
        }
        val order = IntArray(cols * rows) { it }
        for (i in order.lastIndex downTo 1) {
            state += 0x6D2B79F5
            var t = state
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor ((t xor (t ushr 7)) * (t or 61) + t)
            val random = (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
            val j = (random * (i + 1)).toInt()
            order[i] = order[j].also { order[j] = order[i] }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        // Right and bottom remainders outside the grid stay where they are
        canvas.drawBitmap(image, 0f, 0f, null)
        val srcRect = Rect()
        val dstRect = Rect()
        order.forEachIndexed { dst, src ->
            val srcX = src % cols * tileWidth
            val srcY = src / cols * tileHeight
            val dstX = dst % cols * tileWidth
            val dstY = dst / cols * tileHeight
            srcRect.set(srcX, srcY, srcX + tileWidth, srcY + tileHeight)
            dstRect.set(dstX, dstY, dstX + tileWidth, dstY + tileHeight)
            canvas.drawBitmap(image, srcRect, dstRect, null)
        }
        return result
    }

    companion object {
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
