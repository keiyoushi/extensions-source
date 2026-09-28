package eu.kanade.tachiyomi.extension.vi.damconuong

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object UnscrambleInterceptor : Interceptor {
    internal const val QUERY_PARAM = "dcnscramble"
    private const val KEY_PREFIX = "x1."
    private const val KEY_LENGTH = 46
    private const val KEY_SIZE = 32

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val pageKey = request.url.queryParameter(QUERY_PARAM) ?: return chain.proceed(request)

        val newRequest = request.newBuilder()
            .url(request.url.newBuilder().removeAllQueryParameters(QUERY_PARAM).build())
            .build()
        val response = chain.proceed(newRequest)

        val image = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscramble(image, pageKey)

        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()
        image.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody("image/jpeg".toMediaType()))
            .build()
    }

    private fun unscramble(image: Bitmap, pageKey: String): Bitmap {
        val key = decodeKey(pageKey) ?: throw IOException("Unsupported page key")
        val layout = buildLayout(key, image.height) ?: throw IOException("Unsupported page layout")

        val width = image.width
        val result = Bitmap.createBitmap(width, image.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        for (i in 0 until layout.rows) {
            val srcTop = layout.permutation[i] * layout.stripHeight
            val dstTop = i * layout.stripHeight
            canvas.drawBitmap(
                image,
                Rect(0, srcTop, width, srcTop + layout.stripHeight),
                Rect(0, dstTop, width, dstTop + layout.stripHeight),
                null,
            )
        }
        val usedHeight = layout.rows * layout.stripHeight
        if (image.height > usedHeight) {
            val rect = Rect(0, usedHeight, width, image.height)
            canvas.drawBitmap(image, rect, rect, null)
        }

        return result
    }

    private fun decodeKey(pageKey: String): ByteArray? {
        if (pageKey.length != KEY_LENGTH || !pageKey.startsWith(KEY_PREFIX)) return null

        return runCatching {
            Base64.decode(pageKey.substring(KEY_PREFIX.length), Base64.URL_SAFE)
        }.getOrNull()?.takeIf { it.size == KEY_SIZE }
    }

    private class Layout(val rows: Int, val stripHeight: Int, val permutation: IntArray)

    private fun buildLayout(key: ByteArray, imageHeight: Int): Layout? {
        if (imageHeight <= 0) return null

        val random = HmacRandom(key)
        val rows = 8 + random.next(13)
        val stripHeight = 16 * (imageHeight / (16 * rows))
        if (stripHeight < 16) return null

        val permutation = IntArray(rows) { it }
        for (i in rows - 1 downTo 1) {
            val j = random.next(i + 1)
            permutation[i] = permutation[j].also { permutation[j] = permutation[i] }
        }

        return Layout(rows, stripHeight, permutation)
    }

    private class HmacRandom(key: ByteArray) {
        private val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key, "HmacSHA256"))
        }
        private var counter = 0
        private var block = ByteArray(0)
        private var offset = 0

        fun next(bound: Int): Int {
            val limit = (1L shl 32) / bound * bound
            var value = next()

            while (value >= limit) value = next()

            return (value % bound).toInt()
        }

        private fun next(): Long {
            if (offset >= block.size) {
                block = mac.doFinal(
                    byteArrayOf(
                        (counter ushr 24).toByte(),
                        (counter ushr 16).toByte(),
                        (counter ushr 8).toByte(),
                        counter.toByte(),
                    ),
                )
                counter++
                offset = 0
            }

            val value = ((block[offset].toLong() and 0xFF) shl 24) or
                ((block[offset + 1].toLong() and 0xFF) shl 16) or
                ((block[offset + 2].toLong() and 0xFF) shl 8) or
                (block[offset + 3].toLong() and 0xFF)
            offset += 4

            return value
        }
    }
}
