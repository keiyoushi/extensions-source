package eu.kanade.tachiyomi.extension.vi.minotruyen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException

class ImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment ?: return chain.proceed(request)
        if (!fragment.startsWith(fragmentPrefix)) return chain.proceed(request)

        val strips = parseStripMap(fragment.removePrefix(fragmentPrefix))
        val response = chain.proceed(request)
        if (!response.isSuccessful || strips.isEmpty()) return response

        val body = response.body
        val mediaType = body.contentType() ?: "image/jpeg".toMediaType()

        val scrambled = body.byteStream().use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IOException("Failed to decode scrambled image")

        val unscrambled = try {
            Bitmap.createBitmap(scrambled.width, scrambled.height, Bitmap.Config.ARGB_8888)
        } catch (error: Throwable) {
            scrambled.recycle()
            throw error
        }

        return try {
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

            val output = Buffer()
            if (!unscrambled.compress(Bitmap.CompressFormat.JPEG, 90, output.outputStream())) {
                throw IOException("Failed to encode unscrambled image")
            }

            response.newBuilder()
                .body(output.asResponseBody(mediaType))
                .build()
        } finally {
            scrambled.recycle()
            unscrambled.recycle()
        }
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

    private val fragmentPrefix = "mino:"
}
