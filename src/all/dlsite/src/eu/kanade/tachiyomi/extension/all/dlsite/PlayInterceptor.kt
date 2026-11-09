package eu.kanade.tachiyomi.extension.all.dlsite

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

// A DLsite Play page: 128 px tiles in an order seeded from the file name, on an image padded to whole tiles. The
// fragment holds the page's size
class PlayInterceptor(private val host: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val size = request.url.fragment
        if (size == null || request.url.host != host || "optimized" !in request.url.pathSegments) return chain.proceed(request)

        val response = chain.proceed(request.newBuilder().url(request.url.newBuilder().fragment(null).build()).build())
        if (!response.isSuccessful) return response

        val (width, height) = size.split('x').map(String::toInt)
        val scrambled = response.body.byteStream().use(BitmapFactory::decodeStream) ?: throw IOException("Not an image")
        val page = unscramble(scrambled, width, height, request.url.pathSegments.last())
        scrambled.recycle()

        val output = Buffer()
        page.compress(Bitmap.CompressFormat.JPEG, 95, output.outputStream())
        page.recycle()
        return response.newBuilder().body(output.asResponseBody("image/jpeg".toMediaType())).build()
    }

    private fun unscramble(scrambled: Bitmap, width: Int, height: Int, name: String): Bitmap {
        val columns = (width + TILE - 1) / TILE
        val rows = (height + TILE - 1) / TILE
        val order = shuffledTiles(name.substring(5, 12).toLong(16).toInt(), columns * rows)
        val padWidth = scrambled.width - width
        val padHeight = scrambled.height - height

        val page = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        order.forEachIndexed { source, destination ->
            val sx = TILE * (source % columns)
            val sy = TILE * (source / columns)
            val dx = TILE * (destination % columns)
            val dy = TILE * (destination / columns)
            // Tiles in the last column and row hold only the page, not the padding
            val w = if (dx + TILE == TILE * columns) TILE - padWidth else TILE
            val h = if (dy + TILE == TILE * rows) TILE - padHeight else TILE
            canvas.drawBitmap(scrambled, Rect(sx, sy, sx + w, sy + h), Rect(dx, dy, dx + w, dy + h), null)
        }
        return page
    }

    // Fisher-Yates over the tile indices, as the site's viewer does
    private fun shuffledTiles(seed: Int, count: Int): IntArray {
        val random = Mt19937(seed)
        val tiles = IntArray(count) { it }
        for (i in count - 1 downTo 1) {
            val j = (random.nextDouble() * (i + 1)).toInt()
            tiles[i] = tiles[j].also { tiles[j] = tiles[i] }
        }
        return tiles
    }

    companion object {
        private const val TILE = 128
    }
}
