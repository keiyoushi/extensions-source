package eu.kanade.tachiyomi.extension.vi.mimi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class MiMiImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment ?: return chain.proceed(request)
        if (!fragment.startsWith(FRAGMENT_PREFIX)) return chain.proceed(request)

        val drm = fragment.removePrefix(FRAGMENT_PREFIX)
        val decodedMap = MiMiDrmDecoder.decodeMap(drm)

        val cleanRequest = request.newBuilder()
            .url(request.url.newBuilder().fragment(null).build())
            .build()

        val response = chain.proceed(cleanRequest)
        if (!response.isSuccessful) return response

        val body = response.body ?: return response
        val mediaType = body.contentType() ?: "image/jpeg".toMediaType()
        val sourceBytes = body.bytes()

        if (decodedMap == null) {
            return response.newBuilder()
                .body(sourceBytes.toResponseBody(mediaType))
                .build()
        }

        val scrambled = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
            ?: return response.newBuilder().body(sourceBytes.toResponseBody(mediaType)).build()

        val result = Bitmap.createBitmap(scrambled.width, scrambled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        decodedMap.tiles.forEach { tile ->
            if (tile.srcX >= scrambled.width || tile.srcY >= scrambled.height) return@forEach
            if (tile.dstX >= result.width || tile.dstY >= result.height) return@forEach

            val drawWidth = minOf(tile.width, scrambled.width - tile.srcX, result.width - tile.dstX)
            val drawHeight = minOf(tile.height, scrambled.height - tile.srcY, result.height - tile.dstY)
            if (drawWidth <= 0 || drawHeight <= 0) return@forEach

            val srcRect = Rect(tile.srcX, tile.srcY, tile.srcX + drawWidth, tile.srcY + drawHeight)
            val dstRect = Rect(tile.dstX, tile.dstY, tile.dstX + drawWidth, tile.dstY + drawHeight)
            canvas.drawBitmap(scrambled, srcRect, dstRect, null)
        }

        val output = ByteArrayOutputStream()
        val format = if (mediaType.subtype.lowercase() == "png") {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }
        val quality = if (format == Bitmap.CompressFormat.PNG) 100 else 90
        result.compress(format, quality, output)

        scrambled.recycle()
        result.recycle()

        return response.newBuilder()
            .body(output.toByteArray().toResponseBody(mediaType))
            .build()
    }

    companion object {
        const val FRAGMENT_PREFIX = "mimi:"
    }
}
