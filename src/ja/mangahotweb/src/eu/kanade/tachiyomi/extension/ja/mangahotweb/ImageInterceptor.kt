package eu.kanade.tachiyomi.extension.ja.mangahotweb

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

internal const val PUZZLE_FRAGMENT = "puzzle"

// Port of the site reader's Page class: 50 px cells with shifted rows and columns, edges in place
class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.url.fragment != PUZZLE_FRAGMENT || !response.isSuccessful) return response

        val image = response.body.use { BitmapFactory.decodeStream(it.byteStream()) }
        val result = unscramble(image, request.url.pathSegments.last())
        image.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()
        return response.newBuilder().body(buffer.asResponseBody("image/jpeg".toMediaType(), buffer.size)).build()
    }

    private fun unscramble(image: Bitmap, fileName: String): Bitmap {
        val cells = List(image.height / CELL) { y -> List(image.width / CELL) { x -> x to y } }
        // The parity of the digits in the file name gives the order
        val rowsFirst = fileName.filter { it.isDigit() }.sumOf { it.digitToInt() } % 2 == 1
        val sources = if (rowsFirst) shiftColumns(shiftRows(cells)) else shiftRows(shiftColumns(cells))

        val result = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(image, 0f, 0f, null)
        sources.forEachIndexed { y, row ->
            row.forEachIndexed { x, (sourceX, sourceY) ->
                val src = Rect(sourceX * CELL, sourceY * CELL, (sourceX + 1) * CELL, (sourceY + 1) * CELL)
                val dst = Rect(x * CELL, y * CELL, (x + 1) * CELL, (y + 1) * CELL)
                canvas.drawBitmap(image, src, dst, null)
            }
        }
        return result
    }

    // Row i moves its cells by i + 1, to the left on even rows and to the right on odd ones
    private fun <T> shiftRows(rows: List<List<T>>): List<List<T>> = rows.mapIndexed { i, row ->
        val step = if (i % 2 == 0) -(i + 1) else i + 1
        val shifted = MutableList(row.size) { row[it] }
        row.forEachIndexed { j, cell -> shifted[Math.floorMod(j + step, row.size)] = cell }
        shifted
    }

    private fun <T> shiftColumns(rows: List<List<T>>): List<List<T>> = shiftRows(rows.transpose()).transpose()

    private fun <T> List<List<T>>.transpose(): List<List<T>> = if (isEmpty()) this else List(first().size) { x -> List(size) { y -> this[y][x] } }

    companion object {
        private const val CELL = 50
    }
}
