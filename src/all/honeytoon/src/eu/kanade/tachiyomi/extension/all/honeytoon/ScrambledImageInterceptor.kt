package eu.kanade.tachiyomi.extension.all.honeytoon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class ScrambledImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val mime = response.headers["Content-Type"]

        if (mime != "application/octet-stream") {
            return response
        }

        val xPartSizes = response.header("X-Part-Sizes") ?: return response
        val sizes = xPartSizes.split(",").map { it.trim().toInt() }

        val bitmaps = decodeImages(response.body.bytes(), sizes)
        val image = mergeImages(bitmaps)

        val outputStream = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.WEBP, 100, outputStream)

        return response.newBuilder()
            .body(outputStream.toByteArray().toResponseBody("image/webp".toMediaType()))
            .build()
    }

    private fun decodeImages(data: ByteArray, sizes: List<Int>): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        var offset = 0

        for (size in sizes) {
            val part = data.copyOfRange(offset, offset + size)
            val bitmap = BitmapFactory.decodeByteArray(part, 0, part.size)
            if (bitmap != null) {
                bitmaps.add(bitmap)
            }
            offset += size
        }
        return bitmaps
    }

    private fun mergeImages(bitmaps: List<Bitmap>): Bitmap {
        val width = bitmaps.maxOf { it.width }
        val height = bitmaps.sumOf { it.height }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        var currentHeight = 0
        for (bitmap in bitmaps) {
            canvas.drawBitmap(bitmap, 0f, currentHeight.toFloat(), null)
            currentHeight += bitmap.height
        }

        return bitmap
    }
}
