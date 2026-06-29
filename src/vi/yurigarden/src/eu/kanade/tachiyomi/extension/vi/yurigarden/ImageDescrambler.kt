package eu.kanade.tachiyomi.extension.vi.yurigarden

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

/** Descrambles YuriGarden images using key-based strip permutation. */
class ImageDescrambler : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val fragment = response.request.url.fragment ?: return response
        if (!fragment.contains("KEY=")) return response

        val key = fragment.substringAfter("KEY=")
        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
            ?: return response

        val descrambled = unscrambleImage(bitmap, key)

        val output = ByteArrayOutputStream()
        descrambled.compress(Bitmap.CompressFormat.JPEG, 90, output)

        return response.newBuilder()
            .body(output.toByteArray().toResponseBody(MEDIA_TYPE))
            .build()
    }

    private fun unscrambleImage(bitmap: Bitmap, key: String): Bitmap {
        val strips = computeStrips(key, bitmap.height, PARTS)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        var dy = 0
        for (strip in strips) {
            val src = Rect(0, strip.y, bitmap.width, strip.y + strip.h)
            val dst = Rect(0, dy, bitmap.width, dy + strip.h)
            canvas.drawBitmap(bitmap, src, dst, null)
            dy += strip.h
        }
        return result
    }

    private fun computeStrips(key: String, height: Int, parts: Int): List<Strip> {
        val permutation = decodePermutation(key.substring(4), parts)
        val inverse = invertPermutation(permutation)
        val stripHeights = distributeHeight(height - 4 * (parts - 1), parts)

        val mappedHeights = permutation.map { stripHeights[it] }

        var cumulative = 0
        val strips = mappedHeights.mapIndexed { i, h ->
            val y = if (i == 0) 0 else cumulative + 4 * i
            cumulative += h
            Strip(y, h)
        }

        return inverse.map { strips[it] }
    }

    /** Key format: H<base58_data><checksum_char>. */
    private fun decodePermutation(encoded: String, parts: Int): List<Int> {
        val data = encoded.substring(1, encoded.length - 1)
        val checkChar = encoded.last()
        val value = base58Decode(data)

        require(ALPHABET[(value % 58).toInt()] == checkChar) { "Checksum mismatch" }

        return lehmerDecode(value, parts)
    }

    private fun base58Decode(str: String): Long {
        var result = 0L
        for (ch in str) {
            val index = ALPHABET.indexOf(ch)
            result = result * 58 + index
        }
        return result
    }

    private fun lehmerDecode(encoding: Long, size: Int): List<Int> {
        var remaining = encoding
        val available = (0 until size).toMutableList()
        val result = mutableListOf<Int>()

        for (i in size - 1 downTo 0) {
            val factorial = FACTORIALS[i]
            val index = (remaining / factorial).toInt()
            remaining %= factorial
            result.add(available.removeAt(index))
        }
        return result
    }

    private fun invertPermutation(perm: List<Int>): List<Int> {
        val inverse = IntArray(perm.size)
        perm.forEachIndexed { i, v -> inverse[v] = i }
        return inverse.toList()
    }

    private fun distributeHeight(height: Int, parts: Int): List<Int> {
        val base = height / parts
        val remainder = height % parts
        return List(parts) { i -> base + if (i < remainder) 1 else 0 }
    }

    private data class Strip(val y: Int, val h: Int)

    companion object {
        private const val PARTS = 10

        private val MEDIA_TYPE = "image/jpeg".toMediaType()

        /** Base58 alphabet used by scramble keys. */
        private val ALPHABET: String = intArrayOf(
            49, 50, 51, 52, 53, 54, 55, 56, 57,
            65, 66, 67, 68, 69, 70, 71, 72, 74, 75, 76, 77, 78,
            80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90,
            97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107,
            109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119,
            120, 121, 122,
        ).map { it.toChar() }.joinToString("")

        private val FACTORIALS = LongArray(11).also { f ->
            f[0] = 1L
            for (i in 1..10) {
                f[i] = f[i - 1] * i
            }
        }
    }
}
