package eu.kanade.tachiyomi.extension.en.ebookrenta

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.BufferedSource
import kotlin.math.abs

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val prdSer = request.url.fragment?.toIntOrNull()

        if (!response.isSuccessful || prdSer == null) return response

        val page = request.url.pathSegments.last().toInt()
        val result = response.use { unscramble(it.body.source(), page, prdSer) }
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun unscramble(source: BufferedSource, page: Int, prdSer: Int): Bitmap {
        val headerLength = source.readUtf8(9).toLong()
        val header = source.readUtf8(headerLength).split("|")
        val width = header[0].toInt()
        val height = header[1].toInt()
        val tileWidth = width / SPLIT
        val tileHeight = height / SPLIT
        val offsetX = width % SPLIT
        val offsetY = height % SPLIT

        var seed = page + prdSer
        if (seed % 20 == 0) seed = abs(page - prdSer) + 21
        val positions = IntArray(SPLIT * SPLIT)
        for (col in 0 until SPLIT) {
            var column = IntArray(SPLIT) { row -> (row - col).mod(SPLIT) * SPLIT + (2 * col - row).mod(SPLIT) }
            val rounds = ((col + 1) * seed + page / 20) % 20
            for (round in rounds - 1 downTo 0) {
                val order = if (round % 2 == 0) EVEN_ROUND else ODD_ROUND
                column = IntArray(SPLIT) { column[order[it]] }
            }
            column.forEachIndexed { row, tile -> positions[tile] = row * SPLIT + col }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        fun draw(part: String, x: Int, y: Int) {
            val length = part.substringAfter(",", "0").toLong()
            if (length == 0L) return
            val buffer = Buffer()
            source.readFully(buffer, length)
            val bitmap = BitmapFactory.decodeStream(buffer.inputStream())
            canvas.drawBitmap(bitmap, x.toFloat(), y.toFloat(), null)
            bitmap.recycle()
        }

        draw(header[2], 0, 0)
        draw(header[3], 0, 0)
        positions.forEachIndexed { tile, cell ->
            draw(header[tile + 4], cell % SPLIT * tileWidth + offsetX, cell / SPLIT * tileHeight + offsetY)
        }
        return result
    }

    companion object {
        private const val SPLIT = 7
        private val EVEN_ROUND = intArrayOf(1, 0, 3, 2, 5, 4, 6)
        private val ODD_ROUND = intArrayOf(0, 4, 1, 5, 2, 6, 3)
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
