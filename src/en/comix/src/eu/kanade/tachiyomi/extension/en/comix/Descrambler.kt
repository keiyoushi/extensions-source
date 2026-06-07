package eu.kanade.tachiyomi.extension.en.comix

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

object Descrambler {

    private const val GRID_COLS = 5
    private const val GRID_ROWS = 5
    private const val LCG_MULTIPLIER = 1664525
    private const val LCG_INCREMENT = 1013904223
    private const val ENC_MULTIPLIER = 1000005
    private const val ENC_INCREMENT = 1234567891

    val interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) return@Interceptor response

        val body = response.body
        val bodyMediaType = body.contentType()

        response.header("x-enc-seed")?.toLongOrNull()?.toInt()?.let { seed ->
            if (seed == 0) return@Interceptor response

            val length = response.header("x-enc-len")?.toIntOrNull()
                ?: return@Interceptor response
            val bytes = body.bytes()
            decodeEncodedPrefix(bytes, seed, length)

            return@Interceptor response.newBuilder()
                .body(bytes.toResponseBody(bodyMediaType))
                .build()
        }

        val seed = response.header("x-scramble-seed")?.toLongOrNull()?.toInt()
            ?: return@Interceptor response

        if (seed == 0) return@Interceptor response

        val grid = response.header("x-scramble-grid")?.let(::gridFromScrambleHeader)
            ?: Grid(GRID_COLS, GRID_ROWS)

        val bitmap = runCatching {
            body.byteStream().use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
            ?: return@Interceptor response.newBuilder()
                .code(500)
                .message("Failed to decode image")
                .body("Failed to decode image".toResponseBody("text/plain".toMediaType()))
                .build()

        val descrambled = descramble(bitmap, seed, grid)
        bitmap.recycle()

        val output = Buffer()
        descrambled.compress(Bitmap.CompressFormat.JPEG, 90, output.outputStream())
        descrambled.recycle()

        response.newBuilder()
            .body(output.asResponseBody(JPEG_MEDIA, output.size))
            .build()
    }

    private val JPEG_MEDIA = "image/jpeg".toMediaType()

    private data class Grid(val cols: Int, val rows: Int) {
        val size = cols * rows
    }

    private fun descramble(bitmap: Bitmap, seed: Int, grid: Grid): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width < grid.cols || height < grid.rows) return bitmap.copy(Bitmap.Config.ARGB_8888, false)

        val tileW = width / grid.cols
        val tileH = height / grid.rows

        val perm = buildOrder(seed, grid.size)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        canvas.drawBitmap(bitmap, 0f, 0f, null)

        for (srcIdx in 0 until grid.size) {
            val dstIdx = perm[srcIdx]
            val srcCol = srcIdx % grid.cols
            val srcRow = srcIdx / grid.cols
            val dstCol = dstIdx % grid.cols
            val dstRow = dstIdx / grid.cols

            val srcRect = Rect(
                srcCol * tileW,
                srcRow * tileH,
                if (srcCol == grid.cols - 1) width else (srcCol + 1) * tileW,
                if (srcRow == grid.rows - 1) height else (srcRow + 1) * tileH,
            )
            val dstRect = Rect(
                dstCol * tileW,
                dstRow * tileH,
                if (dstCol == grid.cols - 1) width else (dstCol + 1) * tileW,
                if (dstRow == grid.rows - 1) height else (dstRow + 1) * tileH,
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

    private fun decodeEncodedPrefix(bytes: ByteArray, seed: Int, length: Int) {
        var state = seed
        val limit = minOf(length, bytes.size)
        for (i in 0 until limit) {
            state = state * ENC_MULTIPLIER + ENC_INCREMENT
            bytes[i] = (bytes[i].toInt() xor (state ushr 24)).toByte()
        }
    }

    private fun gridFromScrambleHeader(header: String): Grid? {
        val parts = header
            .lowercase()
            .split('x', ',', ':')
            .mapNotNull { it.trim().toIntOrNull() }

        return when (parts.size) {
            1 -> Grid(parts[0], parts[0])
            else -> Grid(parts[0], parts[1])
        }.takeIf { it.cols > 1 && it.rows > 1 }
    }
}
