package eu.kanade.tachiyomi.extension.ar.hijala

import android.graphics.Bitmap
import android.graphics.Canvas
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.nodes.Document
import tachiyomi.decoder.ImageDecoder
import java.text.SimpleDateFormat
import java.util.Locale

class Hijala :
    MangaThemesia(
        "Hijala",
        "https://hijala.com",
        "ar",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
    ) {

    // Site moved from ZeistManga to MangaThemesia again
    override val versionId get() = 2

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::scrambledImageInterceptor)
        .build()

    private fun scrambledImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.host != SCRAMBLED_IMAGE_HOST) {
            return chain.proceed(request)
        }

        val leftPieceUrl = url.queryParameter("leftImage")
            ?: throw IllegalStateException("Missing leftImage")
        val rightPieceUrl = url.queryParameter("rightImage")
            ?: throw IllegalStateException("Missing rightImage")

        val pieceBitmaps = runBlocking {
            listOf(leftPieceUrl, rightPieceUrl).map { pieceUrl ->
                async(Dispatchers.IO) {
                    val pieceRequest = request.newBuilder().url(pieceUrl).build()
                    client.newCall(pieceRequest).await().use { response ->
                        response.body.use { body ->
                            val decoder = ImageDecoder.newInstance(body.byteStream())
                                ?: throw Exception("Failed to create decoder for $pieceUrl")
                            try {
                                decoder.decode() ?: throw Exception("Failed to decode $pieceUrl")
                            } finally {
                                decoder.recycle()
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        require(pieceBitmaps.size == 2) { "Expected exactly 2 bitmaps" }

        val leftBitmap = pieceBitmaps[0]
        val rightBitmap = pieceBitmaps[1]

        val width = leftBitmap.width + rightBitmap.width
        val height = maxOf(leftBitmap.height, rightBitmap.height)

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        try {
            canvas.drawBitmap(leftBitmap, 0f, 0f, null)
            canvas.drawBitmap(rightBitmap, leftBitmap.width.toFloat(), 0f, null)

            val buffer = Buffer().apply {
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream())
            }

            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(buffer.asResponseBody("image/jpeg".toMediaType(), buffer.size))
                .build()
        } finally {
            pieceBitmaps.forEach { it.recycle() }
            resultBitmap.recycle()
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = super.pageListParse(document)
        document.selectFirst("#chapter-pages-js-before") ?: return pages

        val chapterUrl = document.location()
        val pairs = mutableListOf<List<String>>()
        var i = 0
        while (i < pages.size) {
            val pair = pages.subList(i, minOf(i + 2, pages.size))
            if (pair.size == 2) {
                pairs.add(pair.mapNotNull { it.imageUrl })
            }
            i += 2
        }

        return pairs.mapIndexed { index, pair ->
            val imageUrl = HttpUrl.Builder()
                .scheme("http")
                .host(SCRAMBLED_IMAGE_HOST)
                .addQueryParameter("leftImage", pair[0])
                .addQueryParameter("rightImage", pair[1])
                .build()
                .toString()

            Page(index, chapterUrl, imageUrl)
        }
    }
}

private const val SCRAMBLED_IMAGE_HOST = "127.0.0.1"
