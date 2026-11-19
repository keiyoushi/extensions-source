package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/** Serves `<volume file url>#<page index>` by slicing the page out of the cached volume, or the part of it downloaded so far. */
class PageInterceptor(private val store: VolumeStore, private val isVolumeFile: (HttpUrl) -> Boolean) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val pageIndex = request.url.fragment?.toIntOrNull()
        if (pageIndex == null || !isVolumeFile(request.url)) {
            return chain.proceed(request)
        }

        val (isAvif, parts) = store.page(request.url, pageIndex)
        val body = when {
            isAvif -> parts.single().toResponseBody(AVIF)
            parts.size == 1 -> parts.single().toResponseBody(JPEG)
            else -> stitch(parts)
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    private fun stitch(parts: List<ByteArray>): ResponseBody {
        val bitmaps = parts.map { BitmapFactory.decodeByteArray(it, 0, it.size) }
        val width = bitmaps.maxOf { it.width }
        val heights = bitmaps.map { it.height * width / it.width }
        val result = Bitmap.createBitmap(width, heights.sum(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        var top = 0
        bitmaps.forEachIndexed { i, bitmap ->
            canvas.drawBitmap(bitmap, null, Rect(0, top, width, top + heights[i]), null)
            top += heights[i]
            bitmap.recycle()
        }
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()
        return buffer.asResponseBody(JPEG)
    }

    companion object {
        private val AVIF = "image/avif".toMediaType()
        private val JPEG = "image/jpeg".toMediaType()
    }
}
