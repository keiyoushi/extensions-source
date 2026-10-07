package keiyoushi.lib.clipstudioreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import keiyoushi.utils.asJsoup
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import okio.ForwardingSource
import okio.buffer
import org.jsoup.parser.Parser
import java.io.IOException

class ClipStudioReaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val fragment = chain.request().url.fragment.orEmpty()
        return when {
            fragment.startsWith("$PAGE_FRAGMENT,") -> interceptComicPage(chain, fragment)
            fragment.startsWith("$EPUB_FRAGMENT,") -> interceptEpubImage(chain, fragment)
            else -> chain.proceed(chain.request())
        }
    }

    private fun interceptComicPage(chain: Interceptor.Chain, fragment: String): Response {
        val request = chain.request()
        val params = fragment.split(',')
        val gridWidth = params[1].toInt()
        val gridHeight = params[2].toInt()
        val half = params.getOrNull(3)

        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val page = response.asJsoup(Parser.xmlParser())
        // an error such as "trial page over" comes back as a <Result> instead of a <Page>
        val pageNumber = page.selectFirst("PageNo")?.text() ?: throw IOException("Viewer error: ${page.text()}")
        val part = page.select("Kind").first { it.text() in IMAGE_KINDS }
        val imageUrl = request.url.newBuilder()
            .setQueryParameter("mode", part.text())
            .setQueryParameter("file", "${pageNumber.padStart(4, '0')}_${part.attr("No").padStart(4, '0')}.bin")
            .fragment(null)
            .build()

        val image = chain.proceed(request.newBuilder().url(imageUrl).build())
        val isScrambled = part.attr("scramble") == "1"
        if (!image.isSuccessful || (!isScrambled && half == null)) return image

        val mapping = if (isScrambled) page.selectFirst("Scramble")!!.text().split(',').map { it.toInt() } else emptyList()
        val bitmap = image.use { unscramble(BitmapFactory.decodeStream(it.body.byteStream()), mapping, gridWidth, gridHeight, half) }
        val buffer = Buffer()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        bitmap.recycle()

        return image.newBuilder()
            .body(buffer.asResponseBody(JPEG_MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun unscramble(image: Bitmap, mapping: List<Int>, gridWidth: Int, gridHeight: Int, half: String?): Bitmap {
        val height = image.height
        val width = image.width
        val (left, right) = when (half) {
            HALF_LEFT -> 0 to width / 2
            HALF_RIGHT -> width / 2 to width
            else -> 0 to width
        }

        val result = Bitmap.createBitmap(right - left, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.translate(-left.toFloat(), 0f)

        val pieceWidth = width / gridWidth / 8 * 8
        val pieceHeight = height / gridHeight / 8 * 8
        if (mapping.isEmpty() || pieceWidth == 0 || pieceHeight == 0) {
            canvas.drawBitmap(image, 0f, 0f, null)
        } else {
            val src = Rect()
            val dst = Rect()
            mapping.forEachIndexed { i, piece ->
                val dstX = i % gridWidth * pieceWidth
                val dstY = i / gridWidth * pieceHeight
                val srcX = piece % gridWidth * pieceWidth
                val srcY = piece / gridWidth * pieceHeight
                src.set(srcX, srcY, srcX + pieceWidth, srcY + pieceHeight)
                dst.set(dstX, dstY, dstX + pieceWidth, dstY + pieceHeight)
                canvas.drawBitmap(image, src, dst, null)
            }

            val gridRight = pieceWidth * gridWidth
            val gridBottom = pieceHeight * gridHeight
            src.set(gridRight, 0, width, height)
            canvas.drawBitmap(image, src, src, null)
            src.set(0, gridBottom, gridRight, height)
            canvas.drawBitmap(image, src, src, null)
        }

        image.recycle()
        return result
    }

    private fun interceptEpubImage(chain: Interceptor.Chain, fragment: String): Response {
        val (key, token) = fragment.substringAfter(',').split(',', limit = 2)
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        val response = chain.proceed(request)
        if (key.isEmpty() || !response.isSuccessful) return response

        val source = response.body.source()
        source.request(XOR_LENGTH)

        if (IMAGE_SIGNATURES.any { source.rangeEquals(0, it) }) return response

        val xorKey = key.toInt()
        val head = Buffer()
        repeat(minOf(source.buffer.size, XOR_LENGTH).toInt()) { head.writeByte(source.readByte().toInt() xor xorKey) }
        val body = object : ForwardingSource(source) {
            override fun read(sink: Buffer, byteCount: Long) = if (head.size > 0) head.read(sink, byteCount) else super.read(sink, byteCount)
        }

        return response.newBuilder()
            .body(body.buffer().asResponseBody(response.body.contentType(), response.body.contentLength()))
            .build()
    }
}

private val IMAGE_KINDS = setOf("1", "2", "3") // JPEG, GIF, PNG
private val IMAGE_SIGNATURES = listOf("ffd8", "47494638", "89504e47").map { it.decodeHex() }
private const val XOR_LENGTH = 100L
private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
