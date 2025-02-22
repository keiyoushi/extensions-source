package eu.kanade.tachiyomi.extension.zh.boylove

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.InputStream

class UnscramblerInterceptor : Interceptor {
    companion object {
        const val PARTS_COUNT_PARAM = "scrambled_parts_count"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val parts = request.url.queryParameter(PARTS_COUNT_PARAM)?.toIntOrNull()
        return if (parts == null) {
            chain.proceed(request)
        } else {
            val newRequest = request.newBuilder()
                .url(request.url.newBuilder().removeAllQueryParameters(PARTS_COUNT_PARAM).build())
                .build()
            val response = chain.proceed(newRequest)

            val image = response.body.byteStream().use { descramble(it, parts) }

            val body = image.toResponseBody("image/jpeg".toMediaType())
            response.newBuilder().body(body).build()
        }
    }

    private fun descramble(image: InputStream, partsCount: Int): ByteArray {
        val srcBitmap = BitmapFactory.decodeStream(image)
        val width = srcBitmap.width
        val height = srcBitmap.height

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        for (partIndex in 1..partsCount) {
            if (height >= 4000) {
                val stripWidth = width / partsCount
                val x1 = stripWidth * (partIndex - 1)
                val rect = Rect(x1, 0, x1 + stripWidth, height)
                canvas.drawBitmap(srcBitmap, rect, rect, null)
            } else if (partIndex == partsCount) {
                val stripWidth = width - ((width / partsCount) * (partsCount - 1))
                val rectSrc = Rect(0, 0, stripWidth, height)
                val rectDst = Rect(width - stripWidth, 0, width, height)
                canvas.drawBitmap(srcBitmap, rectSrc, rectDst, null)
            } else {
                val stripWidth = width / partsCount
                val xSrc = width - (stripWidth * partIndex)
                val rectSrc = Rect(xSrc, 0, xSrc + stripWidth, height)
                val xDst = stripWidth * (partIndex - 1)
                val rectDst = Rect(xDst, 0, xDst + stripWidth, height)
                canvas.drawBitmap(srcBitmap, rectSrc, rectDst, null)
            }
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 90, output)
        return output.toByteArray()
    }
}
