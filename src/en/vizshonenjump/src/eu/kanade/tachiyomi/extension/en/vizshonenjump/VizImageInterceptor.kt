package eu.kanade.tachiyomi.extension.en.vizshonenjump

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException
import java.io.InputStream

class VizImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (!chain.request().url.toString().contains(IMAGE_URL_ENDPOINT)) {
            return response
        }

        val imageUrl = imageUrlParse(response)
        val imageResponse = chain.proceed(imageRequest(imageUrl, chain.request().header("User-Agent")))

        if (!imageResponse.isSuccessful) {
            imageResponse.close()
            throw IOException(FAILED_TO_FETCH_PAGE_URL)
        }

        val imageBody = imageResponse.decodeImage()

        return imageResponse.newBuilder()
            .body(imageBody)
            .build()
    }

    private fun imageUrlParse(response: Response): String = response.parseAs<PageUrlDto>()
        .data?.values?.firstOrNull() ?: throw IOException(FAILED_TO_FETCH_PAGE_URL)

    private fun imageRequest(url: String, userAgent: String?): Request {
        val headers = Headers.Builder()
            .add("Accept", "*/*")
            .add("Origin", "https://www.viz.com")
            .add("Referer", "https://www.viz.com/")
            .apply {
                if (userAgent != null) {
                    add("User-Agent", userAgent)
                }
            }
            .build()

        return GET(url, headers)
    }

    private fun Response.decodeImage(): ResponseBody {
        val source = body.source()

        // Peak the source to safely fetch image metadata without consuming the actual stream.
        val imageData = source.peek().inputStream().use { it.getImageData().getOrNull() }
            ?: return body

        // Decode directly from the source's InputStream. (This will natively consume the Okio network stream).
        val input = BitmapFactory.decodeStream(source.inputStream())
            ?: return body

        val width = input.width
        val height = input.height
        val newWidth = (width - WIDTH_CUT).coerceAtLeast(imageData.width)
        val newHeight = (height - HEIGHT_CUT).coerceAtLeast(imageData.height)
        val blockWidth = newWidth / CELL_WIDTH_COUNT
        val blockHeight = newHeight / CELL_HEIGHT_COUNT

        val result = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Draw the borders.

        // Top border.
        canvas.drawImage(
            from = input,
            srcX = 0,
            srcY = 0,
            dstX = 0,
            dstY = 0,
            width = newWidth,
            height = blockHeight,
        )
        // Left border.
        canvas.drawImage(
            from = input,
            srcX = 0,
            srcY = blockHeight + 10,
            dstX = 0,
            dstY = blockHeight,
            width = blockWidth,
            height = newHeight - 2 * blockHeight,
        )
        // Bottom border.
        canvas.drawImage(
            from = input,
            srcX = 0,
            srcY = (CELL_HEIGHT_COUNT - 1) * (blockHeight + 10),
            dstX = 0,
            dstY = (CELL_HEIGHT_COUNT - 1) * blockHeight,
            width = newWidth,
            height = height - (CELL_HEIGHT_COUNT - 1) * (blockHeight + 10),
        )
        // Right border.
        canvas.drawImage(
            from = input,
            srcX = (CELL_WIDTH_COUNT - 1) * (blockWidth + 10),
            srcY = blockHeight + 10,
            dstX = (CELL_WIDTH_COUNT - 1) * blockWidth,
            dstY = blockHeight,
            width = blockWidth + (newWidth - CELL_WIDTH_COUNT * blockWidth),
            height = newHeight - 2 * blockHeight,
        )

        // Draw the inner cells.
        for ((m, y) in imageData.key.iterator().withIndex()) {
            canvas.drawImage(
                from = input,
                srcX = (m % INNER_CELL_COUNT + 1) * (blockWidth + 10),
                srcY = (m / INNER_CELL_COUNT + 1) * (blockHeight + 10),
                dstX = (y % INNER_CELL_COUNT + 1) * blockWidth,
                dstY = (y / INNER_CELL_COUNT + 1) * blockHeight,
                width = blockWidth,
                height = blockHeight,
            )
        }

        // Standard rules: write directly to an Okio Buffer to preserve memory efficiency
        val output = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 95, output.outputStream())

        input.recycle()
        result.recycle()

        return output.asResponseBody(JPEG_MEDIA_TYPE)
    }

    private fun Canvas.drawImage(
        from: Bitmap,
        srcX: Int,
        srcY: Int,
        dstX: Int,
        dstY: Int,
        width: Int,
        height: Int,
    ) {
        val srcRect = Rect(srcX, srcY, srcX + width, srcY + height)
        val dstRect = Rect(dstX, dstY, dstX + width, dstY + height)
        drawBitmap(from, srcRect, dstRect, null)
    }

    private fun InputStream.getImageData(): Result<ImageData?> = runCatching {
        val metadata = ImageMetadataReader.readMetadata(this)

        val keyDir = metadata.directories
            .firstOrNull { it.containsTag(ExifSubIFDDirectory.TAG_IMAGE_UNIQUE_ID) }
        val metaUniqueId = keyDir?.getString(ExifSubIFDDirectory.TAG_IMAGE_UNIQUE_ID)
            ?: return@runCatching null

        val sizeDir = metadata.directories.firstOrNull {
            it.containsTag(ExifSubIFDDirectory.TAG_IMAGE_WIDTH) &&
                it.containsTag(ExifSubIFDDirectory.TAG_IMAGE_HEIGHT)
        }
        val metaWidth = sizeDir?.getInt(ExifSubIFDDirectory.TAG_IMAGE_WIDTH) ?: COMMON_WIDTH
        val metaHeight = sizeDir?.getInt(ExifSubIFDDirectory.TAG_IMAGE_HEIGHT) ?: COMMON_HEIGHT

        ImageData(metaWidth, metaHeight, metaUniqueId)
    }

    private class ImageData(val width: Int, val height: Int, val uniqueId: String) {
        val key: List<Int> by lazy {
            uniqueId.split(":")
                .map { it.toInt(16) }
        }
    }

    companion object {
        private const val IMAGE_URL_ENDPOINT = "get_manga_url"
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()

        private const val FAILED_TO_FETCH_PAGE_URL = "Something went wrong while trying to fetch the page."

        private const val CELL_WIDTH_COUNT = 10
        private const val CELL_HEIGHT_COUNT = 15
        private const val INNER_CELL_COUNT = CELL_WIDTH_COUNT - 2

        private const val WIDTH_CUT = 90
        private const val HEIGHT_CUT = 140

        private const val COMMON_WIDTH = 800
        private const val COMMON_HEIGHT = 1200
    }
}
