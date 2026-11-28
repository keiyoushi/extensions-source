package eu.kanade.tachiyomi.multisrc.loneseal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException

// Page `scramble_data`, "<cols>x<rows>:<tile order>", in the image url fragment
class UnscrambleInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = request.url.fragment?.let(ScrambleKey::parse) ?: return chain.proceed(request)
        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val scrambled = response.use { BitmapFactory.decodeStream(it.body.byteStream()) }
            ?: throw IOException("Gagal membaca gambar")
        val tileWidth = scrambled.width / key.cols
        val tileHeight = scrambled.height / key.rows
        if (tileWidth == 0 || tileHeight == 0) {
            scrambled.recycle()
            throw IOException("Gagal membaca gambar")
        }
        val result = Bitmap.createBitmap(tileWidth * key.cols, tileHeight * key.rows, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val src = Rect()
        val dst = Rect()
        key.order.forEachIndexed { from, to ->
            src.setTile(from, key.cols, tileWidth, tileHeight)
            dst.setTile(to, key.cols, tileWidth, tileHeight)
            canvas.drawBitmap(scrambled, src, dst, null)
        }
        scrambled.recycle()

        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.WEBP, 100, buffer.outputStream())
        result.recycle()
        return response.newBuilder()
            .body(buffer.asResponseBody("image/webp".toMediaType(), buffer.size))
            .build()
    }

    private fun Rect.setTile(index: Int, cols: Int, width: Int, height: Int) {
        val x = index % cols * width
        val y = index / cols * height
        set(x, y, x + width, y + height)
    }
}

internal class ScrambleKey(val cols: Int, val rows: Int, val order: List<Int>) {
    companion object {
        private val regex = """(\d+)x(\d+):(\d+(?:,\d+)*)""".toRegex()

        fun parse(data: String): ScrambleKey? {
            val (cols, rows, order) = regex.matchEntire(data)?.destructured ?: return null
            val key = ScrambleKey(
                cols.toIntOrNull() ?: return null,
                rows.toIntOrNull() ?: return null,
                order.split(',').map { it.toIntOrNull() ?: return null },
            )
            return key.takeIf { it.cols > 0 && it.rows > 0 && it.order.size == it.cols * it.rows }
        }
    }
}
