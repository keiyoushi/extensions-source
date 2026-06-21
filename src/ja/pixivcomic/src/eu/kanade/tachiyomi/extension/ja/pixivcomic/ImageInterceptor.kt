package eu.kanade.tachiyomi.extension.ja.pixivcomic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import keiyoushi.utils.readIntLittleEndian
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.security.MessageDigest
import kotlin.math.ceil

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty() || !fragment.startsWith("key=") || !response.isSuccessful) {
            return response
        }

        val key = fragment.substringAfter("key=")
        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscramble(bitmap, key)
        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun unscramble(image: Bitmap, key: String): Bitmap {
        val width = image.width
        val height = image.height
        val columns = width / GRID_SIZE
        val rows = ceil(height.toFloat() / GRID_SIZE).toInt()

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val src = Rect()
        val dst = Rect()

        val random = Xoshiro128(MessageDigest.getInstance("SHA-256").digest((SHUFFLE_SALT + key).toByteArray()))
        repeat(WARMUP) { random.next() }

        for (row in 0 until rows) {
            val sources = sourceColumns(columns, random)
            val top = row * GRID_SIZE
            val bottom = minOf(top + GRID_SIZE, height)

            for (column in 0 until columns) {
                val from = sources[column] * GRID_SIZE
                val to = column * GRID_SIZE
                src.set(from, top, from + GRID_SIZE, bottom)
                dst.set(to, top, to + GRID_SIZE, bottom)
                canvas.drawBitmap(image, src, dst, null)
            }
        }

        val shuffledWidth = columns * GRID_SIZE
        if (shuffledWidth < width) {
            src.set(shuffledWidth, 0, width, height)
            canvas.drawBitmap(image, src, src, null)
        }

        return result
    }

    private fun sourceColumns(columns: Int, random: Xoshiro128): IntArray {
        val permutation = IntArray(columns) { it }
        for (i in columns - 1 downTo 1) {
            val j = (random.next() % (i + 1).toUInt()).toInt()
            permutation[i] = permutation[j].also { permutation[j] = permutation[i] }
        }

        val source = IntArray(columns)
        for (i in permutation.indices) {
            source[permutation[i]] = i
        }
        return source
    }

    private class Xoshiro128(seed: ByteArray) {
        private var s0 = seed.readIntLittleEndian(0).toUInt()
        private var s1 = seed.readIntLittleEndian(4).toUInt()
        private var s2 = seed.readIntLittleEndian(8).toUInt()
        private var s3 = seed.readIntLittleEndian(12).toUInt()

        init {
            if (s0 == 0u && s1 == 0u && s2 == 0u && s3 == 0u) s0 = 1u
        }

        fun next(): UInt {
            val result = (s1 * 5u).rotateLeft(7) * 9u
            val t = s1 shl 9

            s2 = s2 xor s0
            s3 = s3 xor s1
            s1 = s1 xor s2
            s0 = s0 xor s3
            s2 = s2 xor t
            s3 = s3.rotateLeft(11)

            return result
        }
    }

    companion object {
        private const val SHUFFLE_SALT = "4wXCKprMMoxnyJ3PocJFs4CYbfnbazNe"
        private const val GRID_SIZE = 32
        private const val WARMUP = 100
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
