package eu.kanade.tachiyomi.extension.en.vizshonenjump

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import de.stefan_oltmann.kim.Kim
import de.stefan_oltmann.kim.android.readMetadata
import de.stefan_oltmann.kim.format.tiff.constant.ExifTag
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.InputStream

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty() || fragment != "scramble" || !response.isSuccessful) return response

        val source = response.body.source()
        val contentLength = response.body.contentLength()
        val key = runCatching {
            source.peek().inputStream().use { readScrambleKey(it, contentLength) }
        }.getOrNull() ?: return response

        val bitmap = BitmapFactory.decodeStream(source.inputStream())
        val result = unscramble(bitmap, key)
        bitmap.recycle()

        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun unscramble(image: Bitmap, key: List<Int>): Bitmap {
        val width = image.width - (CELL_WIDTH_COUNT - 1) * GAP
        val height = image.height - (CELL_HEIGHT_COUNT - 1) * GAP

        val blockWidth = width / CELL_WIDTH_COUNT
        val blockHeight = height / CELL_HEIGHT_COUNT
        val strideWidth = blockWidth + GAP
        val strideHeight = blockHeight + GAP

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val srcRect = Rect()
        val dstRect = Rect()

        fun draw(srcX: Int, srcY: Int, srcW: Int, srcH: Int, dstX: Int, dstY: Int) {
            srcRect.set(srcX, srcY, srcX + srcW, srcY + srcH)
            dstRect.set(dstX, dstY, dstX + srcW, dstY + srcH)
            canvas.drawBitmap(image, srcRect, dstRect, null)
        }

        draw(0, 0, width, blockHeight, 0, 0)

        draw(0, strideHeight, blockWidth, height - 2 * blockHeight, 0, blockHeight)

        val lastRowSrcY = (CELL_HEIGHT_COUNT - 1) * strideHeight
        draw(0, lastRowSrcY, width, image.height - lastRowSrcY, 0, (CELL_HEIGHT_COUNT - 1) * blockHeight)

        val lastColSrcX = (CELL_WIDTH_COUNT - 1) * strideWidth
        val rightBlockWidth = blockWidth + (width - CELL_WIDTH_COUNT * blockWidth)
        draw(lastColSrcX, strideHeight, rightBlockWidth, height - 2 * blockHeight, (CELL_WIDTH_COUNT - 1) * blockWidth, blockHeight)

        for ((sourceIndex, destIndex) in key.withIndex()) {
            val srcX = (sourceIndex % INNER_CELL_COUNT + 1) * strideWidth
            val srcY = (sourceIndex / INNER_CELL_COUNT + 1) * strideHeight
            val dstX = (destIndex % INNER_CELL_COUNT + 1) * blockWidth
            val dstY = (destIndex / INNER_CELL_COUNT + 1) * blockHeight
            draw(srcX, srcY, blockWidth, blockHeight, dstX, dstY)
        }
        return result
    }

    private fun readScrambleKey(inputStream: InputStream, length: Long): List<Int>? {
        val metadata = Kim.readMetadata(inputStream, length) ?: return null
        val uniqueId = metadata.findStringValue(ExifTag.EXIF_TAG_IMAGE_UNIQUE_ID) ?: return null
        return uniqueId.split(":").map { it.toInt(16) }
    }

    companion object {
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
        private const val CELL_WIDTH_COUNT = 10
        private const val CELL_HEIGHT_COUNT = 15
        private const val INNER_CELL_COUNT = CELL_WIDTH_COUNT - 2
        private const val GAP = 10
    }
}
