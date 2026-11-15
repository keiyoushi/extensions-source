package eu.kanade.tachiyomi.extension.ja.jumptoon

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
        val fragment = request.url.fragment

        if (!response.isSuccessful || fragment == null || !fragment.contains(":")) return response

        val (algorithm, seed, pageWidth) = fragment.split(":", limit = 3)
        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscramble(bitmap, ALGORITHMS.getValue(algorithm), seed.toLong(), pageWidth.toInt())

        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.WEBP, 100, buffer.outputStream())
        result.recycle()
        val body = buffer.asResponseBody(MEDIA_TYPE, buffer.size)

        return response.newBuilder()
            .body(body)
            .build()
    }

    private class Algorithm(
        val splitWidth: Int,
        val paddingWidth: Int,
        val blankWidth: Int,
    ) {
        val cellWidth = splitWidth + blankWidth + 2 * paddingWidth
    }

    private fun unscramble(
        image: Bitmap,
        algorithm: Algorithm,
        seed: Long,
        pageWidth: Int,
    ): Bitmap {
        val height = image.height
        val columns = image.width / algorithm.cellWidth
        val remainder = pageWidth % algorithm.splitWidth

        val order = IntArray(columns) { it }
        var state = seed
        for (i in (if (remainder != 0) columns - 1 else columns) downTo 2) {
            state = (1664525L * state + 1013904223L) and 0xFFFFFFFFL // ranqd1 - https://en.wikipedia.org/wiki/Linear_congruential_generator#Parameters_in_common_use
            val j = (state % i).toInt()
            order[j] = order[i - 1].also { order[i - 1] = order[j] }
        }
        val source = IntArray(columns)
        for (value in order) source[order[value]] = value

        val result = Bitmap.createBitmap(columns * algorithm.splitWidth + remainder, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val srcRect = Rect()
        val dstRect = Rect()

        for (destIndex in 0..columns) {
            val width = if (destIndex < columns) algorithm.splitWidth else remainder
            if (width == 0) continue
            val srcX = (if (destIndex < columns) source[destIndex] else columns) * algorithm.cellWidth + algorithm.paddingWidth
            val dstX = destIndex * algorithm.splitWidth

            srcRect.set(srcX, 0, srcX + width, height)
            dstRect.set(dstX, 0, dstX + width, height)
            canvas.drawBitmap(image, srcRect, dstRect, null)
        }

        return result
    }

    companion object {
        private val MEDIA_TYPE = "image/webp".toMediaType()
        private val ALGORITHMS = mapOf(
            "V1" to Algorithm(splitWidth = 12, paddingWidth = 3, blankWidth = 3),
            "V2" to Algorithm(splitWidth = 20, paddingWidth = 15, blankWidth = 1),
        )
    }
}
