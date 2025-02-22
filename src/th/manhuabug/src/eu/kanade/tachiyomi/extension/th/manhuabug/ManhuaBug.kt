package eu.kanade.tachiyomi.extension.th.manhuabug

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class ManhuaBug : Madara(
    "ManhuaBug",
    "https://www.manhuabug.com",
    "th",
    dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("en")),
) {
    override val supportsLatest = false

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val filterNonMangaItems = false

    override val fetchGenres = false // While genres exist, they can't be used in standard Madara search

    // Descrambling logic from ManhuaKey
    override val client = super.client.newBuilder()
        .addNetworkInterceptor(::imageDescrambler)
        .build()

    override val pageListParseSelector = ".reading-content img, .reading-content div.displayImage + script:containsData(p,a,c,k,e,d)"

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }
        val location = document.location()

        return document.select(pageListParseSelector).mapIndexed { idx, element ->
            if (element.tagName().equals("img")) {
                Page(idx, location, imageFromElement(element))
            } else {
                val unpackedScript = Unpacker.unpack(element.data())
                val blockWidth = blockWidthRegex.find(unpackedScript)!!.groupValues[1].toInt()
                val blockHeight = blockHeightRegex.find(unpackedScript)!!.groupValues[1].toInt()
                val matrix = unpackedScript.substringAfter("[")
                    .substringBefore("];")
                    .let { "[$it]" }
                val scrambledImageUrl = unpackedScript.substringAfter("url(")
                    .substringBefore(");")

                val data = ScramblingData(
                    blockWidth = blockWidth,
                    blockHeight = blockHeight,
                    matrix = json.decodeFromString(matrix),
                )

                Page(idx, location, "$scrambledImageUrl#${json.encodeToString(data)}")
            }
        }
    }

    private val blockWidthRegex = Regex("""width:\s*"?\s*\+?\s*(\d+)\s*\+?\s*"?px;""")
    private val blockHeightRegex = Regex("""height:\s*"?\s*\+?\s*(\d+)\s*\+?\s*"?px;""")

    @Serializable
    class ScramblingData(
        val blockWidth: Int,
        val blockHeight: Int,
        val matrix: List<List<Double>>,
    )

    private fun imageDescrambler(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.url.fragment.isNullOrEmpty()) {
            return response
        }

        val scramblingData = json.decodeFromString<ScramblingData>(request.url.fragment!!)

        val scrambledImg = BitmapFactory.decodeStream(response.body.byteStream())
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

        val output = ByteArrayOutputStream()
        descrambledImg.compress(Bitmap.CompressFormat.JPEG, 90, output)

        val image = output.toByteArray()
        val body = image.toResponseBody("image/jpeg".toMediaType())

        return response.newBuilder()
            .body(body)
            .build()
    }
}
