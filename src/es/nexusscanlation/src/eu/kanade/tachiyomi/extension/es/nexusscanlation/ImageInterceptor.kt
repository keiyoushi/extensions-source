package eu.kanade.tachiyomi.extension.es.nexusscanlation

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

        val fragment = request.url.fragment ?: return response

        if (!fragment.startsWith("scramble=")) {
            return response
        }

        val parts = fragment
            .removePrefix("scramble=")
            .split(',')

        val cols = parts[0].toInt()
        val rows = parts[1].toInt()
        val seed = parts[2].toUInt()
        val version = parts.getOrNull(3)?.toIntOrNull() ?: 1

        val body = response.body

        val bitmap = body.byteStream().use { BitmapFactory.decodeStream(it) } ?: return response

        val decoded = descramble(
            bitmap = bitmap,
            cols = cols,
            rows = rows,
            seed = seed,
            version = version,
        )
        bitmap.recycle()

        val buffer = Buffer()

        decoded.compress(
            Bitmap.CompressFormat.PNG,
            90,
            buffer.outputStream(),
        )
        decoded.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody("image/png".toMediaType()))
            .build()
    }

    private fun descramble(
        bitmap: Bitmap,
        cols: Int,
        rows: Int,
        seed: UInt,
        version: Int,
    ): Bitmap {
        val tileWidth = bitmap.width / cols
        val tileHeight = bitmap.height / rows

        val count = cols * rows

        val rng = Mulberry32(seed)

        val permutation = IntArray(count) { it }
        for (i in count - 1 downTo 1) {
            val j = (rng.nextDouble() * (i + 1)).toInt()
            val tmp = permutation[i]
            permutation[i] = permutation[j]
            permutation[j] = tmp
        }

        val flips = if (version >= 2) {
            IntArray(count) {
                (rng.nextDouble() * 4).toInt()
            }
        } else {
            null
        }

        val result = Bitmap.createBitmap(
            tileWidth * cols,
            tileHeight * rows,
            Bitmap.Config.ARGB_8888,
        )

        val canvas = Canvas(result)

        val srcRect = Rect()
        val dstRect = Rect()
        val tileRect = Rect(0, 0, tileWidth, tileHeight)

        for (srcIndex in 0 until count) {
            val srcX = (srcIndex % cols) * tileWidth
            val srcY = (srcIndex / cols) * tileHeight

            val dstIndex = permutation[srcIndex]

            val dstX = (dstIndex % cols) * tileWidth
            val dstY = (dstIndex / cols) * tileHeight

            srcRect.set(
                srcX,
                srcY,
                srcX + tileWidth,
                srcY + tileHeight,
            )

            val flip = flips?.get(srcIndex) ?: 0
            if (flip == 0) {
                dstRect.set(
                    dstX,
                    dstY,
                    dstX + tileWidth,
                    dstY + tileHeight,
                )
                canvas.drawBitmap(
                    bitmap,
                    srcRect,
                    dstRect,
                    null,
                )
            } else {
                val flipH = (flip and 1) != 0
                val flipV = (flip and 2) != 0

                val transX = if (flipH) (dstX + tileWidth).toFloat() else dstX.toFloat()
                val transY = if (flipV) (dstY + tileHeight).toFloat() else dstY.toFloat()
                val scaleX = if (flipH) -1f else 1f
                val scaleY = if (flipV) -1f else 1f

                canvas.save()
                canvas.translate(transX, transY)
                canvas.scale(scaleX, scaleY)
                canvas.drawBitmap(
                    bitmap,
                    srcRect,
                    tileRect,
                    null,
                )
                canvas.restore()
            }
        }

        return result
    }

    private class Mulberry32(seed: UInt) {

        private var state = seed

        fun nextDouble(): Double {
            state += 0x6D2B79F5u
            var t = state

            t = ((t xor (t shr 15)) * (1u or t))

            t = t xor (
                t + ((t xor (t shr 7)) * (61u or t))
                )

            return ((t xor (t shr 14)).toDouble()) / 4294967296.0
        }
    }
}
