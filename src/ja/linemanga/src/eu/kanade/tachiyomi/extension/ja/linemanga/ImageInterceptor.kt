package eu.kanade.tachiyomi.extension.ja.linemanga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

private class PortalPageMetadata(
    val hc: Int, // horizontal block count
    val bwd: Int, // block width/height in px
    val m: List<String>, // scramble map (base-35 encoded values)
)

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (!response.isSuccessful || fragment.isNullOrEmpty() || !fragment.contains(":")) return response

        val parts = fragment.split(':')
        val metadata = PortalPageMetadata(
            hc = parts[0].toInt(),
            bwd = parts[1].toInt(),
            m = parts.subList(2, parts.size),
        )

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscramble(bitmap, metadata)

        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()
        val body = buffer.asResponseBody("image/jpeg".toMediaType(), buffer.size)

        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun unscramble(image: Bitmap, metadata: PortalPageMetadata): Bitmap {
        val width = image.width
        val height = image.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val srcRect = Rect()
        val dstRect = Rect()

        val gridWidth = metadata.hc * metadata.bwd
        val gridHeight = metadata.m.size / metadata.hc * metadata.bwd

        if (gridWidth < width) {
            srcRect.set(gridWidth, 0, width, height)
            canvas.drawBitmap(image, srcRect, srcRect, null)
        }
        if (gridHeight < height) {
            srcRect.set(0, gridHeight, gridWidth, height)
            canvas.drawBitmap(image, srcRect, srcRect, null)
        }

        //   m[i] decoded from base-35 gives the source block index.
        //   Block i in reading order is the destination.
        for (i in metadata.m.indices) {
            val o = metadata.m[i].toLong(35).toInt()

            val srcX = (o % metadata.hc) * metadata.bwd
            val srcY = (o / metadata.hc) * metadata.bwd
            val dstX = (i % metadata.hc) * metadata.bwd
            val dstY = (i / metadata.hc) * metadata.bwd

            srcRect.set(srcX, srcY, srcX + metadata.bwd, srcY + metadata.bwd)
            dstRect.set(dstX, dstY, dstX + metadata.bwd, dstY + metadata.bwd)

            canvas.drawBitmap(image, srcRect, dstRect, null)
        }

        return result
    }
}
