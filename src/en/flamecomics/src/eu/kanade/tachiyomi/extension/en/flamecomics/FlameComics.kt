package eu.kanade.tachiyomi.extension.en.flamecomics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import java.io.ByteArrayOutputStream

class FlameComics : MangaThemesia(
    "Flame Comics",
    "https://flamecomics.xyz",
    "en",
    mangaUrlDirectory = "/series",
) {

    // Flame Scans -> Flame Comics
    override val id = 6350607071566689772

    override val client = super.client.newBuilder()
        .rateLimit(2, 7)
        .addInterceptor(::composedImageIntercept)
        .build()

    override val pageSelector = "div#readerarea img:not(noscript img)[class*=wp-image]"

    // Split Image Fixer Start
    private val composedSelector: String = "#readerarea div.figure_container div.composed_figure"

    override fun pageListParse(document: Document): List<Page> {
        val hasSplitImages = document
            .select(composedSelector)
            .firstOrNull() != null

        if (!hasSplitImages) {
            return super.pageListParse(document)
        }

        return document.select("#readerarea p:has(img), $composedSelector").toList()
            .filter {
                it.select("img").all { imgEl ->
                    imgEl.attr("abs:src").isNullOrEmpty().not()
                }
            }
            .mapIndexed { i, el ->
                if (el.tagName() == "p") {
                    Page(i, "", el.select("img").attr("abs:src"))
                } else {
                    val imageUrls = el.select("img")
                        .joinToString("|") { it.attr("abs:src") }

                    Page(i, document.location(), imageUrls + COMPOSED_SUFFIX)
                }
            }
    }

    private fun composedImageIntercept(chain: Interceptor.Chain): Response {
        if (!chain.request().url.toString().endsWith(COMPOSED_SUFFIX)) {
            return chain.proceed(chain.request())
        }

        val imageUrls = chain.request().url.toString()
            .removeSuffix(COMPOSED_SUFFIX)
            .split("%7C")

        var width = 0
        var height = 0

        val imageBitmaps = imageUrls.map { imageUrl ->
            val request = chain.request().newBuilder().url(imageUrl).build()
            val response = chain.proceed(request)

            val bitmap = BitmapFactory.decodeStream(response.body.byteStream())

            width += bitmap.width
            height = bitmap.height

            bitmap
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        var left = 0

        imageBitmaps.forEach { bitmap ->
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = Rect(left, 0, left + bitmap.width, bitmap.height)

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)

            left += bitmap.width
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.PNG, 100, output)

        val responseBody = output.toByteArray().toResponseBody(MEDIA_TYPE)

        return Response.Builder()
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .request(chain.request())
            .message("OK")
            .body(responseBody)
            .build()
    }
    // Split Image Fixer End

    companion object {
        private const val COMPOSED_SUFFIX = "?comp"
        private val MEDIA_TYPE = "image/png".toMediaType()
    }
}
