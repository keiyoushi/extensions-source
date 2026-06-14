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
    private const val NUM_TILES = GRID_COLS * GRID_ROWS

    private const val ENC_MULTIPLIER = 1000005
    private const val ENC_INCREMENT = 1234567891

    private val JPEG_MEDIA = "image/jpeg".toMediaType()

    val interceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isSuccessful) return@Interceptor response

        val rawScrambleSeed = response.header("x-scramble-seed")
        val rawScrambleGrid = response.header("x-scramble-grid")
        val rawScrambleAlgo = response.header("x-scramble-algo")
        val rawEncSeed = response.header("x-enc-seed")

        val encSeed = rawEncSeed?.toLongOrNull()?.toInt()
        val encLen = response.header("x-enc-len")?.toIntOrNull()
        val scrambleSeed = rawScrambleSeed?.toLongOrNull()?.toInt()

        val needsXor = encSeed != null && encSeed != 0 && encLen != null
        val shouldDescrambleGrid = rawScrambleGrid == "5x5" && rawScrambleAlgo == "3" &&
            scrambleSeed != null && scrambleSeed != 0

        if (!needsXor && !shouldDescrambleGrid) return@Interceptor response

        val body = response.body
        val bodyMediaType = body.contentType()

        val originalBytes = body.bytes()
        val bytes = if (needsXor) {
            decodeEncodedBytes(originalBytes, encSeed!!, encLen!!)
        } else {
            originalBytes
        }

        if (shouldDescrambleGrid) {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@Interceptor response.newBuilder()
                    .code(500)
                    .message("Failed to decode image")
                    .body("Failed to decode image".toResponseBody("text/plain".toMediaType()))
                    .build()

            val descrambled = descramble(bitmap, scrambleSeed!!)
            bitmap.recycle()

            val output = Buffer()
            descrambled.compress(Bitmap.CompressFormat.JPEG, 90, output.outputStream())
            descrambled.recycle()

            return@Interceptor response.newBuilder()
                .removeHeader("Content-Length")
                .removeHeader("Content-Type")
                .body(output.asResponseBody(JPEG_MEDIA, output.size))
                .build()
        }

        response.newBuilder()
            .body(bytes.toResponseBody(bodyMediaType))
            .build()
    }

    private fun decodeEncodedBytes(bytes: ByteArray, seed: Int, length: Int): ByteArray {
        val result = bytes.copyOf()
        var state = seed
        val limit = minOf(result.size, length)
        for (i in 0 until limit) {
            state = state * ENC_MULTIPLIER + ENC_INCREMENT
            result[i] = (result[i].toInt() xor (state ushr 24)).toByte()
        }
        return result
    }

    private fun descramble(bitmap: Bitmap, seed: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val tileW = width / GRID_COLS
        val tileH = height / GRID_ROWS
        val order = buildOrder(seed, NUM_TILES)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        for (dstIdx in 0 until NUM_TILES) {
            val srcIdx = order[dstIdx]
            val srcCol = srcIdx % GRID_COLS
            val srcRow = srcIdx / GRID_COLS
            val dstCol = dstIdx % GRID_COLS
            val dstRow = dstIdx / GRID_COLS
            val srcRect = Rect(srcCol * tileW, srcRow * tileH, (srcCol + 1) * tileW, (srcRow + 1) * tileH)
            val dstRect = Rect(dstCol * tileW, dstRow * tileH, (dstCol + 1) * tileW, (dstRow + 1) * tileH)
            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        }
        return output
    }

    private fun buildOrder(seed: Int, n: Int): IntArray {
        val arr = IntArray(n) { it }
        var state = seed or 1
        for (i in n - 1 downTo 1) {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            val j = (state.toLong() and 0xFFFFFFFFL) % (i + 1)
            val tmp = arr[i]
            arr[i] = arr[j.toInt()]
            arr[j.toInt()] = tmp
        }
        return IntArray(n).also { inverse ->
            for (i in arr.indices) {
                inverse[arr[i]] = i
            }
        }
    }
}
