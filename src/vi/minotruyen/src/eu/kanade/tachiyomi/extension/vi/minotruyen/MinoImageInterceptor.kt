package eu.kanade.tachiyomi.extension.vi.minotruyen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class MinoImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment ?: return chain.proceed(request)
        if (!fragment.startsWith(FRAGMENT_PREFIX)) return chain.proceed(request)

        val strips = parseStripMap(fragment.removePrefix(FRAGMENT_PREFIX))
        val cleanRequest = request.newBuilder()
            .url(request.url.newBuilder().fragment(null).build())
            .build()

        val response = chain.proceed(cleanRequest)
        if (!response.isSuccessful || strips.isEmpty()) return response

        val mediaType = response.body.contentType() ?: "image/jpeg".toMediaType()
        val sourceBytes = response.body.bytes()

        val scrambled = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
            ?: return response.newBuilder().body(sourceBytes.toResponseBody(mediaType)).build()

        val unscrambled = Bitmap.createBitmap(scrambled.width, scrambled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(unscrambled)

        var srcY = 0
        for ((destY, height) in strips) {
            if (srcY >= scrambled.height || destY >= unscrambled.height) break

            val drawHeight = minOf(height, scrambled.height - srcY, unscrambled.height - destY)
            if (drawHeight <= 0) {
                srcY += height
                continue
            }

            val srcRect = Rect(0, srcY, scrambled.width, srcY + drawHeight)
            val dstRect = Rect(0, destY, unscrambled.width, destY + drawHeight)
            canvas.drawBitmap(scrambled, srcRect, dstRect, null)
            srcY += height
        }

        val output = ByteArrayOutputStream()
        unscrambled.compress(Bitmap.CompressFormat.JPEG, 90, output)

        scrambled.recycle()
        unscrambled.recycle()

        return response.newBuilder()
            .body(output.toByteArray().toResponseBody(mediaType))
            .build()
    }

    private fun parseStripMap(value: String): List<Pair<Int, Int>> = value.split(',')
        .mapNotNull { token ->
            val destY = token.substringBefore('-').toIntOrNull()
            val height = token.substringAfter('-', "").toIntOrNull()
            if (destY == null || height == null || destY < 0 || height <= 0) {
                null
            } else {
                destY to height
            }
        }

    companion object {
        private const val FRAGMENT_PREFIX = "mino:"
    }
}
