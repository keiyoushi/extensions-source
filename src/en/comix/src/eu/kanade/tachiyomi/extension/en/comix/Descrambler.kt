package eu.kanade.tachiyomi.extension.en.comix

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

object Descrambler {

    private const val GRID_COLS = 5
    private const val GRID_ROWS = 5
    private const val NUM_TILES = GRID_COLS * GRID_ROWS
    private const val LCG_MULTIPLIER = 1664525
    private const val LCG_INCREMENT = 1013904223

    val interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) return@Interceptor response

        val seed = response.header("x-scramble-seed")?.toLongOrNull()?.toInt()
            ?: return@Interceptor response

        if (seed == 0) return@Interceptor response

        val body = response.body ?: return@Interceptor response
        val imageBytes = body.bytes()

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return@Interceptor response.newBuilder()
                .body(imageBytes.toResponseBody(body.contentType()))
                .build()

        val descrambled = descramble(bitmap, seed)
        bitmap.recycle()

        val output = Buffer()
        descrambled.compress(Bitmap.CompressFormat.JPEG, 90, output.outputStream())
        descrambled.recycle()

        response.newBuilder()
            .body(output.readByteString().toResponseBody(JPEG_MEDIA))
            .build()
    }

    private val JPEG_MEDIA = "image/jpeg".toMediaType()

    private fun descramble(bitmap: Bitmap, seed: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val tileW = width / GRID_COLS
        val tileH = height / GRID_ROWS

        val perm = buildOrder(seed, NUM_TILES)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        for (srcIdx in 0 until NUM_TILES) {
            val dstIdx = perm[srcIdx]
            val srcCol = srcIdx % GRID_COLS
            val srcRow = srcIdx / GRID_COLS
            val dstCol = dstIdx % GRID_COLS
            val dstRow = dstIdx / GRID_COLS

            val srcRect = Rect(
                srcCol * tileW,
                srcRow * tileH,
                (srcCol + 1) * tileW,
                (srcRow + 1) * tileH,
            )
            val dstRect = Rect(
                dstCol * tileW,
                dstRow * tileH,
                (dstCol + 1) * tileW,
                (dstRow + 1) * tileH,
            )

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        }

        return output
    }

    private fun buildOrder(seed: Int, n: Int): IntArray {
        val arr = IntArray(n) { it }
        var state = seed
        for (i in n - 1 downTo 1) {
            state = state * LCG_MULTIPLIER + LCG_INCREMENT
            val j = (state.toLong() and 0xFFFFFFFFL) % (i + 1)
            val tmp = arr[i]
            arr[i] = arr[j.toInt()]
            arr[j.toInt()] = tmp
        }
        return arr
    }
}
