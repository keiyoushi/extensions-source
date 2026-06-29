package eu.kanade.tachiyomi.extension.zh.roumanwu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.security.MessageDigest

class ScrambledImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url
        if ("sr:1" !in url.pathSegments) return response
        val image = response.body.use { BitmapFactory.decodeStream(it.byteStream()) }
        val width = image.width
        val height = image.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // /_next/static/chunks/app/books/[bookId]/[ind]/page-eaacab94dbec1fa4.js
        // Scrambled images are reversed by blocks. Remainder is included in the bottom (scrambled) block.
        val blocks = url.pathSegments.last().substringBeforeLast('.')
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

        val responseBody = Buffer().run {
            result.compress(Bitmap.CompressFormat.JPEG, 90, outputStream())
            asResponseBody("image/jpeg".toMediaType())
        }
        return response.newBuilder().body(responseBody).build()
    }

    private fun Byte.toPositiveInt() = toInt() and 0xFF
}
