package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException

/** Answers the store's page addresses with the page, stacking a legacy page's strips into one image. */
internal class PageInterceptor(private val store: VolumeStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = try {
            when (val page = store.page(request.url, chain.call()::isCanceled)) {
                null -> null
                is PageImage.Whole -> page.bytes.toResponseBody(page.type)
                is PageImage.Strips -> stitch(page.jpegs)
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Throwable) {
            // Anything but an IOException thrown from an interceptor crashes the app
            throw IOException(e.message, e)
        } ?: return chain.proceed(request)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    /** Draws the strips one at a time, so only one is decoded at once next to the result. */
    private fun stitch(strips: List<ByteArray>): ResponseBody {
        val sizes = strips.map { strip ->
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(strip, 0, strip.size, this)
            }
        }
        if (sizes.any { it.outWidth <= 0 || it.outHeight <= 0 }) throw IOException("Unsupported page image")
        val width = sizes.maxOf { it.outWidth }
        val heights = sizes.map { it.outHeight * width / it.outWidth }
        val result = Bitmap.createBitmap(width, heights.sum(), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(result)
            var top = 0
            strips.forEachIndexed { i, strip ->
                val bitmap = BitmapFactory.decodeByteArray(strip, 0, strip.size) ?: throw IOException("Unsupported page image")
                canvas.drawBitmap(bitmap, null, Rect(0, top, width, top + heights[i]), null)
                bitmap.recycle()
                top += heights[i]
            }
            val buffer = Buffer()
            result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
            return buffer.asResponseBody(JPEG)
        } finally {
            result.recycle()
        }
    }

    private companion object {
        val JPEG = "image/jpeg".toMediaType()
    }
}
