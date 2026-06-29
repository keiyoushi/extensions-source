package eu.kanade.tachiyomi.extension.th.mangakimi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.lib.unpacker.Unpacker
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.nodes.Document
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MangaKimi :
    MangaThemesia(
        "MangaKimi",
        "https://www.mangakimi.com",
        "th",
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Bangkok")
        },
    ) {

    override val client = super.client.newBuilder()
        .addNetworkInterceptor(::imageDescrambler)
        .build()

    // Pages
    override val pageSelector = "div#readerarea img, #readerarea div.displayImage + script:containsData(p,a,c,k,e,d)"

    override fun pageListParse(document: Document): List<Page> {
        val location = document.location()

        val pages = document.select(pageSelector).mapNotNull { element ->
            try {
                if (element.tagName() == "img") {
                    val url = element.imgAttr()
                    url.ifEmpty { null }
                } else {
                    val unpackedScript = Unpacker.unpack(element.data())
                    if (unpackedScript.isEmpty()) return@mapNotNull null

                    val blockWidth = blockWidthRegex.find(unpackedScript)?.groupValues?.get(1)?.toInt() ?: 0
                    val blockHeight = blockHeightRegex.find(unpackedScript)?.groupValues?.get(1)?.toInt() ?: 0

                    val matrixStr = unpackedScript.substringAfter("[", "").substringBefore("];", "")
                    if (matrixStr.isEmpty()) return@mapNotNull null

                    val matrix = "[$matrixStr]".parseAs<List<List<Double>>>()

                    val scrambledImageUrl = unpackedScript.substringAfter("url(")
                        .substringBefore(");")
                        .trim('\'', '"')

                    if (scrambledImageUrl.isEmpty()) return@mapNotNull null

                    val data = ScramblingData(
                        blockWidth = blockWidth,
                        blockHeight = blockHeight,
                        matrix = matrix,
                    )

                    "$scrambledImageUrl#${data.toJsonString()}"
                }
            } catch (_: Exception) {
                null
            }
        }.mapIndexed { i, url ->
            Page(i, location, url)
        }

        // We only call countViews if we parsed pages directly to avoid double counting
        // since the super fallback also calls it.
        if (pages.isNotEmpty()) {
            countViews(document)
            return pages
        }

        // Fallback to super method if no pages are found (e.g., for JSON image lists)
        return super.pageListParse(document)
    }

    private val blockWidthRegex = Regex("""width:\s*["']?\s*\+?\s*(\d+)\s*\+?\s*["']?px;""")
    private val blockHeightRegex = Regex("""height:\s*["']?\s*\+?\s*(\d+)\s*\+?\s*["']?px;""")

    @Serializable
    class ScramblingData(
        val blockWidth: Int,
        val blockHeight: Int,
        val matrix: List<List<Double>>,
    )

    private fun imageDescrambler(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val fragment = request.url.fragment
        if (fragment.isNullOrEmpty()) {
            return response
        }

        val scramblingData = try {
            fragment.parseAs<ScramblingData>()
        } catch (_: Exception) {
            return response
        }

        val responseBody = response.body
        val scrambledImg = responseBody.byteStream().use {
            BitmapFactory.decodeStream(it)
        } ?: throw IOException("Failed to decode descrambling image")

        val descrambledImg = Bitmap.createBitmap(scrambledImg.width, scrambledImg.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(descrambledImg)

        for (pos in scramblingData.matrix) {
            val srcRect = Rect(
                pos[2].toInt(),
                pos[3].toInt(),
                pos[2].toInt() + scramblingData.blockWidth,
                pos[3].toInt() + scramblingData.blockHeight,
            )
            val destRect = Rect(
                pos[0].toInt(),
                pos[1].toInt(),
                pos[0].toInt() + scramblingData.blockWidth,
                pos[1].toInt() + scramblingData.blockHeight,
            )
            canvas.drawBitmap(scrambledImg, srcRect, destRect, null)
        }

        val buffer = Buffer()
        descrambledImg.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())

        // Free native memory early
        scrambledImg.recycle()
        descrambledImg.recycle()

        val body = buffer.asResponseBody("image/jpeg".toMediaType())

        return response.newBuilder()
            .body(body)
            .build()
    }
}
