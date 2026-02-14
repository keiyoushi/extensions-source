package eu.kanade.tachiyomi.extension.ja.ynjn

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.url.fragment != "scramble") {
            return response
        }

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val blockWidth = width / 4
        val blockHeight = height / 4
        val srcRect = Rect()
        val dstRect = Rect()

        for (i in 0 until 16) {
            val row = i / 4
            val col = i % 4

            val srcX = col * blockWidth
            val srcY = row * blockHeight
            val dstX = row * blockWidth
            val dstY = col * blockHeight

            srcRect.set(srcX, srcY, srcX + blockWidth, srcY + blockHeight)
            dstRect.set(dstX, dstY, dstX + blockWidth, dstY + blockHeight)

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        }

        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody("image/jpeg".toMediaType(), buffer.size))
            .build()
    }
}
