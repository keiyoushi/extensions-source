package eu.kanade.tachiyomi.lib.speedbinb

import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.lib.speedbinb.descrambler.PtBinbDescramblerA
import eu.kanade.tachiyomi.lib.speedbinb.descrambler.PtBinbDescramblerF
import eu.kanade.tachiyomi.lib.speedbinb.descrambler.PtImgDescrambler
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class SpeedBinbInterceptor(private val json: Json) : Interceptor {

    private val textInterceptor by lazy { TextInterceptor() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val filename = request.url.pathSegments.last()
        val fragment = request.url.fragment

        return when {
            host == TextInterceptorHelper.HOST -> textInterceptor.intercept(chain)
            filename.endsWith(".ptimg.json") -> interceptPtImg(chain, request)
            fragment == null -> chain.proceed(request)
            fragment.startsWith("ptbinb,") -> interceptPtBinB(chain, request)
            else -> chain.proceed(request)
        }
    }

    private fun interceptPtImg(chain: Interceptor.Chain, request: Request): Response {
        val response = chain.proceed(request)
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
            .body(descrambler.descrambleImage(image)!!.toResponseBody(JPEG_MEDIA_TYPE))
            .build()
    }

    private fun interceptPtBinB(chain: Interceptor.Chain, request: Request): Response {
        val response = chain.proceed(request)
        val fragment = request.url.fragment!!
        val (s, u) = fragment.removePrefix("ptbinb,").split(",", limit = 2)

        if (s.isEmpty() && u.isEmpty()) {
            return response
        }

        val imageData = response.body.bytes()
        val image = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        val descrambler = if (s[0] == '=' && u[0] == '=') {
            PtBinbDescramblerF(s, u, image.width, image.height)
        } else if (NUMERIC_CHARACTERS.contains(s[0]) && NUMERIC_CHARACTERS.contains(u[0])) {
            PtBinbDescramblerA(s, u, image.width, image.height)
        } else {
            throw IOException("Cannot select descrambler for key pair s=$s, u=$u")
        }
        val descrambled = descrambler.descrambleImage(image) ?: imageData

        return response.newBuilder()
            .body(descrambled.toResponseBody(JPEG_MEDIA_TYPE))
            .build()
    }
}

private const val NUMERIC_CHARACTERS = "0123456789"
private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
