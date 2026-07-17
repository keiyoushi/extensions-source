package eu.kanade.tachiyomi.extension.en.kmanga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

// https://greasyfork.org/en/scripts/467901-k-manga-ripper
class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (!response.isSuccessful || fragment.isNullOrEmpty() || !fragment.contains(":")) {
            return response
        }

        val (seed, titleId, episodeId) = fragment.split(":")
        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscramble(bitmap, seed, titleId.toInt(), episodeId.toInt())

        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()
        val body = buffer.asResponseBody(MEDIA_TYPE, buffer.size)

        return response.newBuilder()
            .body(body)
            .build()
    }

    private class Coord(val x: Int, val y: Int)
    private class CoordPair(val source: Coord, val dest: Coord)

    private fun UInt.xorshift32(): UInt {
        var n = this
        n = n xor (n shl 13)
        n = n xor (n shr 17)
        n = n xor (n shl 5)
        return n
    }

    private fun getUnscrambledCoords(seed: String, titleId: Int, episodeId: Int): List<CoordPair> {
        // WASM decrypts two 10-byte arrays to use as substitution charsets.
        // Selects the charset based on titleId % 2
        val charset = if (titleId % 2 == 0) CHARSET_EVEN else CHARSET_ODD

        var parsedInt = 0UL

        // Maps the string into a base-10 number using the selected charset
        for (char in seed) {
            val index = charset.indexOf(char)
            if (index != -1) {
                parsedInt = parsedInt * 10UL + index.toULong()
            } else {
                break
            }
        }

        // The final 32-bit seed is xor'd against the sum of titleId and episodeId
        var seed32 = parsedInt.toUInt() xor (titleId.toUInt() + episodeId.toUInt())

        val pairs = mutableListOf<Pair<UInt, Int>>()

        for (i in 0 until 16) {
            seed32 = seed32.xorshift32()
            pairs.add(seed32 to i)
        }

        pairs.sortBy { it.first }

        return pairs.mapIndexed { destIndex, (_, sourceIndex) ->
            CoordPair(
                source = Coord(x = sourceIndex % GRID_SIZE, y = sourceIndex / GRID_SIZE),
                dest = Coord(x = destIndex % GRID_SIZE, y = destIndex / GRID_SIZE),
            )
        }
    }

    private fun unscramble(image: Bitmap, seed: String, titleId: Int, episodeId: Int): Bitmap {
        val unscrambledCoords = getUnscrambledCoords(seed, titleId, episodeId)
        val width = image.width
        val height = image.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val blockWidth = (width / 8 * 8) / 4
        val blockHeight = (height / 8 * 8) / 4
        val srcRect = Rect()
        val dstRect = Rect()

        unscrambledCoords.forEach {
            val srcX = it.source.x * blockWidth
            val srcY = it.source.y * blockHeight
            val dstX = it.dest.x * blockWidth
            val dstY = it.dest.y * blockHeight

            srcRect.set(srcX, srcY, srcX + blockWidth, srcY + blockHeight)
            dstRect.set(dstX, dstY, dstX + blockWidth, dstY + blockHeight)

            canvas.drawBitmap(image, srcRect, dstRect, null)
        }

        val processedWidth = blockWidth * GRID_SIZE
        val processedHeight = blockHeight * GRID_SIZE

        if (width > processedWidth) {
            srcRect.set(processedWidth, 0, width, height)
            canvas.drawBitmap(image, srcRect, srcRect, null)
        }
        if (height > processedHeight) {
            srcRect.set(0, processedHeight, processedWidth, height)
            canvas.drawBitmap(image, srcRect, srcRect, null)
        }

        return result
    }

    companion object {
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
        private const val GRID_SIZE = 4
        private const val CHARSET_EVEN = "we7ru3ty8i"
        private const val CHARSET_ODD = "h4xm9bqz1p"
    }
}
