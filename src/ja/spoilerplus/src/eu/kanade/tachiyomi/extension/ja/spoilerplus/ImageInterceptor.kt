package eu.kanade.tachiyomi.extension.ja.spoilerplus

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

// Pages are served as a 4x4 grid of shuffled cells; the fragment holds the chapter's cell order
class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val order = request.url.fragment?.split(",")?.map(String::toInt)
        if (order == null || !response.isSuccessful) return response

        val scrambled = response.body.byteStream().use(BitmapFactory::decodeStream)
            ?: throw IOException("画像を読み込めませんでした")
        val result = scrambled.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val cellWidth = scrambled.width / GRID
        val cellHeight = scrambled.height / GRID
        val src = Rect()
        val dst = Rect()
        order.forEachIndexed { index, from ->
            src.set(from % GRID * cellWidth, from / GRID * cellHeight, (from % GRID + 1) * cellWidth, (from / GRID + 1) * cellHeight)
            dst.set(index % GRID * cellWidth, index / GRID * cellHeight, (index % GRID + 1) * cellWidth, (index / GRID + 1) * cellHeight)
            canvas.drawBitmap(scrambled, src, dst, null)
        }
        scrambled.recycle()

        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.WEBP, 90, buffer.outputStream())
        result.recycle()
        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    companion object {
        private const val GRID = 4
        private val MEDIA_TYPE = "image/webp".toMediaType()
    }
}
