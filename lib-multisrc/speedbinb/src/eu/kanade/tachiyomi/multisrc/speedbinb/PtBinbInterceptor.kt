package eu.kanade.tachiyomi.multisrc.speedbinb

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import eu.kanade.tachiyomi.multisrc.speedbinb.descrambler.PtBinbDescramblerA
import eu.kanade.tachiyomi.multisrc.speedbinb.descrambler.PtBinbDescramblerF
import eu.kanade.tachiyomi.multisrc.speedbinb.descrambler.PtImgDescrambler
import eu.kanade.tachiyomi.multisrc.speedbinb.descrambler.SpeedBinbDescrambler
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException

class PtBinbInterceptor(private val json: Json) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val pathSegments = request.url.pathSegments
        val fragment = request.url.fragment

        return when {
            pathSegments.last().endsWith(".ptimg.json") -> interceptPtImg(chain, request, response)
            fragment == null -> response
            fragment.startsWith("ptbinb,") -> interceptPtBinb(request, response)
            else -> response
        }
    }

    private fun interceptPtImg(chain: Interceptor.Chain, request: Request, response: Response): Response {
        val metadata = json.decodeFromString<PtImg>(response.body.string())
        val imageUrl = request.url.newBuilder()
            .setPathSegment(request.url.pathSize - 1, metadata.resources.i.src)
            .build()
        val imageResponse = chain.proceed(
            request.newBuilder().url(imageUrl).build(),
        )

        if (metadata.translations.isEmpty()) {
            return imageResponse
        }

        val image = BitmapFactory.decodeStream(imageResponse.body.byteStream())
        val descrambler = PtImgDescrambler(metadata)

        return imageResponse.newBuilder()
            .body(descrambleImage(image, descrambler)!!.toResponseBody(JPEG_MEDIA_TYPE))
            .build()
    }

    private fun interceptPtBinb(request: Request, response: Response): Response {
        val fragment = request.url.fragment!!
        val (s, u) = fragment.removePrefix("ptbinb,").split(",", limit = 2)

        if (s.isEmpty() && u.isEmpty()) {
            return response
        }

        val imageData = response.body.bytes()
        val image = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        val descrambler = if (s[0] == '=' && u[0] == '=') {
            PtBinbDescramblerF(s, u, image.width, image.height)
        } else if (STARTS_WITH_NUMBER_REGEX.matches(s) && STARTS_WITH_NUMBER_REGEX.matches(u)) {
            PtBinbDescramblerA(s, u, image.width, image.height)
        } else {
            throw IOException("Cannot select descrambler for key pair s=$s, u=$u")
        }
        val descrambled = descrambleImage(image, descrambler) ?: imageData

        return response.newBuilder()
            .body(descrambled.toResponseBody(JPEG_MEDIA_TYPE))
            .build()
    }

    private fun descrambleImage(image: Bitmap, descrambler: SpeedBinbDescrambler): ByteArray? {
        if (!descrambler.isScrambled()) {
            return null
        }

        val (width, height) = descrambler.getCanvasDimensions()
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        descrambler.getDescrambleCoords().forEach {
            val src = Rect(it.xsrc, it.ysrc, it.xsrc + it.width, it.ysrc + it.height)
            val dst = Rect(it.xdest, it.ydest, it.xdest + it.width, it.ydest + it.height)

            canvas.drawBitmap(image, src, dst, null)
        }

        return ByteArrayOutputStream()
            .also {
                result.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            .toByteArray()
    }
}

private val STARTS_WITH_NUMBER_REGEX = Regex("""^\d""")
private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
