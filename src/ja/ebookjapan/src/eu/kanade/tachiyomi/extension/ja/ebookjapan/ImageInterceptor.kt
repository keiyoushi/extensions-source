package eu.kanade.tachiyomi.extension.ja.ebookjapan

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
import java.nio.ByteBuffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty() || !response.isSuccessful) return response

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val scramble = Scramble.decode(fragment)
        val result = scramble.unscramble(bitmap)
        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.WEBP, 100, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    companion object {
        private val MEDIA_TYPE = "image/webp".toMediaType()
    }
}

// WASM func 211 (shuffle)
class Scramble(
    private val width: Int,
    private val height: Int,
    private val margin: Int,
    private val unit: Int,
    private val generated: Boolean,
    private val table: ByteArray,
) {
    fun encode(): String {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + table.size)
            .putShort(width.toShort())
            .putShort(height.toShort())
            .put(margin.toByte())
            .put(unit.toByte())
            .put(if (generated) 1.toByte() else 0.toByte())
            .put(table)

        return Base64.encodeToString(buffer.array(), BASE64_FLAGS)
    }

    fun unscramble(source: Bitmap): Bitmap {
        val page = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)

        if (generated) {
            drawGenerated(canvas, source)
        } else {
            drawTable(canvas, source)
        }

        return page
    }

    // newer books: walk a shrinking list of cells, turning every tile a quarter further than the last
    private fun drawGenerated(canvas: Canvas, source: Bitmap) {
        val columns = width.ceilDiv(unit)
        val total = columns * height.ceilDiv(unit)
        val cell = unit + margin * 2

        // the first five bytes seed the stride, the last five the starting cell, all ten the turns
        val head = (0 until 5).sumOf { table[it].toInt() and 0xFF }
        val sum = head + (5 until 10).sumOf { table[it].toInt() and 0xFF }
        val stride = 2 + head % (total - 3)
        val start = 2 + (sum - head) % (total - 3)
        val turn = if (sum and 8 != 0) 1 else 3
        var rotation = (sum shr 1) and 3

        val cells = IntArray(total) { it }
        var left = total
        val src = Rect()
        val dst = Rect()
        var x = 0
        var y = 0

        for (index in 0 until total) {
            val position = (index * stride + start) % left
            val origin = cells[position]
            left--
            System.arraycopy(cells, position + 1, cells, position, left - position)

            val sx = origin % columns * cell + margin
            val sy = origin / columns * cell + margin
            src.set(sx, sy, sx + unit, sy + unit)
            dst.set(x, y, x + unit, y + unit)
            canvas.save()
            canvas.rotate(rotation * 90f, dst.exactCenterX(), dst.exactCenterY())
            canvas.drawBitmap(source, src, dst, null)
            canvas.restore()

            rotation = (rotation + turn) and 3
            x += unit
            if (x >= width) {
                x = 0
                y += unit
            }
        }
    }

    // older books: one lookup byte per tile, never turned
    private fun drawTable(canvas: Canvas, source: Bitmap) {
        val twice = margin * 2
        val tileWidth = width.ceilDiv(unit).roundUpCell(twice)
        val tileHeight = height.ceilDiv(unit).roundUpCell(twice)
        val cellWidth = tileWidth + twice
        val cellHeight = tileHeight + twice
        val src = Rect()
        val dst = Rect()
        var x = 0
        var y = 0

        for (entry in table) {
            if (x < width && y < height) {
                val origin = entry.toInt() and 0xFF
                val sx = origin % unit * cellWidth + margin
                val sy = origin / unit * cellHeight + margin
                val visibleWidth = minOf(tileWidth, width - x)
                val visibleHeight = minOf(tileHeight, height - y)
                src.set(sx, sy, sx + visibleWidth, sy + visibleHeight)
                dst.set(x, y, x + visibleWidth, y + visibleHeight)
                canvas.drawBitmap(source, src, dst, null)
            }

            x += tileWidth
            if (x >= width) {
                x = 0
                y += tileHeight
            }
        }
    }

    private fun Int.ceilDiv(divisor: Int) = (this + divisor - 1) / divisor

    private fun Int.roundUpCell(twice: Int): Int {
        val remainder = (this + twice) and 7
        return if (remainder == 0) this else this - remainder + 8
    }

    companion object {
        private const val HEADER_SIZE = 7
        private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

        fun decode(fragment: String): Scramble {
            val buffer = ByteBuffer.wrap(Base64.decode(fragment, BASE64_FLAGS))
            val width = buffer.short.toInt() and 0xFFFF
            val height = buffer.short.toInt() and 0xFFFF
            val margin = buffer.get().toInt() and 0xFF
            val unit = buffer.get().toInt() and 0xFF
            val generated = buffer.get().toInt() != 0
            val table = ByteArray(buffer.remaining()).also(buffer::get)
            return Scramble(width, height, margin, unit, generated, table)
        }
    }
}
