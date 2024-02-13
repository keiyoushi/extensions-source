package eu.kanade.tachiyomi.extension.en.xcalibrscans

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class AntiScrapInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.fragment != ANTI_SCRAP_FRAGMENT) {
            return chain.proceed(request)
        }

        val imageUrls = request.url
            .queryParameter("urls").orEmpty()
            .split(IMAGE_URLS_SEPARATOR)

        var width = 0
        var height = 0

        val imageBitmaps = imageUrls.map { imageUrl ->
            val newRequest = request.newBuilder().url(imageUrl).build()
            val response = chain.proceed(newRequest)

            val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
            response.close()

            width += bitmap.width
            height = bitmap.height

            bitmap
        }

        val mergedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(mergedBitmap).apply {
            // Will mirror everything that are applied afterwards
            scale(-1F, 1F, width / 2F, height / 2F)

            // Merge the bitmaps vertically
            var left = 0
            imageBitmaps.forEach { bitmap ->
                val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                val dstRect = Rect(left, 0, left + bitmap.width, bitmap.height)

                drawBitmap(bitmap, srcRect, dstRect, null)

                left += bitmap.width
            }
        }

        val baos = ByteArrayOutputStream()
        mergedBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)

        return Response.Builder()
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .request(request)
            .message("OK")
            .body(baos.toByteArray().toResponseBody(pngMediaType))
            .build()
    }

    companion object {
        const val ANTI_SCRAP_FRAGMENT = "ANTI_SCRAP"

        const val IMAGE_URLS_SEPARATOR = "|"

        val pngMediaType = "image/png".toMediaType()
    }
}
