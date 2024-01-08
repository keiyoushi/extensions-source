package eu.kanade.tachiyomi.extension.zh.roumanwu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

object ScrambledImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url.toString()
        if (!url.endsWith(SCRAMBLED_SUFFIX)) return response
        val image = BitmapFactory.decodeStream(response.body.byteStream())
        val width = image.width
        val height = image.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // https://rouman01.xyz/_next/static/chunks/pages/books/%5Bbookid%5D/%5Bid%5D-6f60a589e82dc8db.js
        // Scrambled images are reversed by blocks. Remainder is included in the bottom (scrambled) block.
        val blocks = url.removeSuffix(SCRAMBLED_SUFFIX).substringAfterLast('/').removeSuffix(".jpg")
            .let { Base64.decode(it, Base64.DEFAULT) }
            .let { MessageDigest.getInstance("MD5").digest(it) } // thread-safe
            .let { it.last().toPositiveInt() % 10 + 5 }
        val blockHeight = height / blocks
        var iy = blockHeight * (blocks - 1)
        var cy = 0
        for (i in 0 until blocks) {
            val h = if (i == 0) height - iy else blockHeight
            val src = Rect(0, iy, width, iy + h)
            val dst = Rect(0, cy, width, cy + h)
            canvas.drawBitmap(image, src, dst, null)
            iy -= blockHeight
            cy += h
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 90, output)
        val responseBody = output.toByteArray().toResponseBody(jpegMediaType)
        return response.newBuilder().body(responseBody).build()
    }

    private val jpegMediaType = "image/jpeg".toMediaType()
    private fun Byte.toPositiveInt() = toInt() and 0xFF
    const val SCRAMBLED_SUFFIX = "#scrambled"
}
