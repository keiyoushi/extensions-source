package eu.kanade.tachiyomi.extension.all.mangafire

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
import kotlin.math.min

object ImageInterceptor : Interceptor {

    const val SCRAMBLED = "scrambled"
    private const val PIECE_SIZE = 200
    private const val MIN_SPLIT_COUNT = 5

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment ?: return response
        if (SCRAMBLED !in fragment) return response
        val offset = fragment.substringAfterLast('_').toInt()

        val image = response.body.byteStream().use { descramble(it, offset) }
        val body = image.toResponseBody("image/jpeg".toMediaType())
        return response.newBuilder().body(body).build()
    }

    private fun descramble(image: InputStream, offset: Int): ByteArray {
        // obfuscated code: https://mangafire.to/assets/t1/min/all.js
        // it shuffles arrays of the image slices

        val bitmap = BitmapFactory.decodeStream(image)
        val width = bitmap.width
        val height = bitmap.height

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val pieceWidth = min(PIECE_SIZE, width.ceilDiv(MIN_SPLIT_COUNT))
        val pieceHeight = min(PIECE_SIZE, height.ceilDiv(MIN_SPLIT_COUNT))
        val xMax = width.ceilDiv(pieceWidth) - 1
        val yMax = height.ceilDiv(pieceHeight) - 1

        for (y in 0..yMax) {
            for (x in 0..xMax) {
                val xDst = pieceWidth * x
                val yDst = pieceHeight * y
                val w = min(pieceWidth, width - xDst)
                val h = min(pieceHeight, height - yDst)

                val xSrc = pieceWidth * when (x) {
                    xMax -> x // margin
                    else -> (xMax - x + offset) % xMax
                }
                val ySrc = pieceHeight * when (y) {
                    yMax -> y // margin
                    else -> (yMax - y + offset) % yMax
                }

                val srcRect = Rect(xSrc, ySrc, xSrc + w, ySrc + h)
                val dstRect = Rect(xDst, yDst, xDst + w, yDst + h)

                canvas.drawBitmap(bitmap, srcRect, dstRect, null)
            }
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 90, output)
        return output.toByteArray()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Int.ceilDiv(other: Int) = (this + (other - 1)) / other
}
