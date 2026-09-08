package eu.kanade.tachiyomi.extension.vi.cuutruyen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val drmData = request.url.fragment
            ?.takeIf { it.startsWith("drm_data=") }
            ?.removePrefix("drm_data=")
            ?.takeIf(String::isNotEmpty)
            ?: return chain.proceed(request)
        val imageRequest = request.newBuilder()
            .url(request.url.newBuilder().fragment(null).build())
            .build()
        val response = chain.proceed(imageRequest)
        if (!response.isSuccessful) return response

        return response.use {
            val mediaType = it.body.contentType()
            val source = it.body.byteStream().use(BitmapFactory::decodeStream)
                ?: throw IOException("Failed to decode scrambled image")
            val result = try {
                unscramble(source, drmData)
            } finally {
                source.recycle()
            }

            try {
                val output = Buffer()
                if (!result.compress(Bitmap.CompressFormat.JPEG, 95, output.outputStream())) {
                    throw IOException("Failed to encode unscrambled image")
                }
                it.newBuilder()
                    .body(output.asResponseBody(mediaType))
                    .build()
            } finally {
                result.recycle()
            }
        }
    }

    private fun unscramble(source: Bitmap, drmData: String): Bitmap {
        val decoded = Base64.decode(drmData, Base64.DEFAULT)
        val key = decryptionKey.toByteArray()
        val mapping = decoded.mapIndexed { index, byte ->
            (byte.toInt() xor key[index % key.size].toInt()).toByte()
        }.toByteArray().toString(Charsets.UTF_8)

        if (!mapping.startsWith(drmVersion)) {
            throw IOException("Unsupported CuuTruyen DRM data")
        }

        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        var sourceY = 0
        mapping.split('|').drop(1).forEach { tile ->
            val (destinationY, height) = tile.split('-', limit = 2).map(String::toInt)
            val sourceRect = Rect(0, sourceY, source.width, sourceY + height)
            val destinationRect = Rect(0, destinationY, source.width, destinationY + height)
            canvas.drawBitmap(source, sourceRect, destinationRect, null)
            sourceY += height
        }
        return result
    }

    private val drmVersion = "#v4|"
    private val decryptionKey = "3141592653589793"
}
