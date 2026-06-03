package eu.kanade.tachiyomi.extension.es.nexusscanlation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class ImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val fragment = request.url.fragment ?: return response

        if (!fragment.startsWith("scramble=")) {
            return response
        }

        val (cols, rows, seed) = fragment
            .removePrefix("scramble=")
            .split(',')
            .let {
                Triple(
                    it[0].toInt(),
                    it[1].toInt(),
                    it[2].toUInt(),
                )
            }

        val body = response.body ?: return response
        val contentType = body.contentType()

        val imageBytes = body.bytes()

        val bitmap = BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size,
        ) ?: return response

        val decoded = descramble(
            bitmap = bitmap,
            cols = cols,
            rows = rows,
            seed = seed,
        )

        val output = ByteArrayOutputStream()

        decoded.compress(
            Bitmap.CompressFormat.PNG,
            90,
            output,
        )

        return response.newBuilder()
            .body(output.toByteArray().toResponseBody(contentType))
            .build()
    }

    private fun descramble(
        bitmap: Bitmap,
        cols: Int,
        rows: Int,
        seed: UInt,
    ): Bitmap {
        val tileWidth = bitmap.width / cols
        val tileHeight = bitmap.height / rows

        val count = cols * rows

        val permutation = shuffledIndices(
            count = count,
            seed = seed,
        )

        val result = Bitmap.createBitmap(
            tileWidth * cols,
            tileHeight * rows,
            Bitmap.Config.ARGB_8888,
        )

        val canvas = Canvas(result)

        for (srcIndex in 0 until count) {
            val srcX = (srcIndex % cols) * tileWidth
            val srcY = (srcIndex / cols) * tileHeight

            val tile = Bitmap.createBitmap(
                bitmap,
                srcX,
                srcY,
                tileWidth,
                tileHeight,
            )

            val dstIndex = permutation[srcIndex]

            val dstX = (dstIndex % cols) * tileWidth
            val dstY = (dstIndex / cols) * tileHeight

            canvas.drawBitmap(
                tile,
                dstX.toFloat(),
                dstY.toFloat(),
                null,
            )

            tile.recycle()
        }

        return result
    }

    private fun shuffledIndices(
        count: Int,
        seed: UInt,
    ): IntArray {
        val result = IntArray(count) { it }

        val rng = Mulberry32(seed)

        for (i in count - 1 downTo 1) {
            val j = (rng.nextDouble() * (i + 1)).toInt()

            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
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
