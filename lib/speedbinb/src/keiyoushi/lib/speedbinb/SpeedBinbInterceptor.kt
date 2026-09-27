package keiyoushi.lib.speedbinb

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import keiyoushi.lib.speedbinb.descrambler.PtBinbDescramblerA
import keiyoushi.lib.speedbinb.descrambler.PtBinbDescramblerF
import keiyoushi.lib.speedbinb.descrambler.PtImgDescrambler
import keiyoushi.lib.speedbinb.descrambler.SpeedBinbDescrambler
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException

class SpeedBinbInterceptor : Interceptor {
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
            fragment.startsWith("ptbinb,") -> interceptPtBinb(chain, request, fragment)
            else -> chain.proceed(request)
        }
    }

    private fun interceptPtImg(chain: Interceptor.Chain, request: Request): Response {
        val metadata = chain.proceed(request).parseAs<PtImg>()
        val imageUrl = request.url.newBuilder()
            .setPathSegment(request.url.pathSize - 1, metadata.resources.i.src)
            .build()
        val response = chain.proceed(
            request.newBuilder().url(imageUrl).build(),
        )

        return response.descramble(PtImgDescrambler(metadata))
    }

    private fun interceptPtBinb(chain: Interceptor.Chain, request: Request, fragment: String): Response {
        val (s, u) = fragment.removePrefix("ptbinb,").split(",", limit = 2)

        if (s.isEmpty() && u.isEmpty()) {
            return chain.proceed(request)
        }

        val descrambler = if (s[0] == '=' && u[0] == '=') {
            PtBinbDescramblerF(s, u)
        } else if (NUMERIC_CHARACTERS.contains(s[0]) && NUMERIC_CHARACTERS.contains(u[0])) {
            PtBinbDescramblerA(s, u)
        } else {
            throw IOException("Cannot select descrambler for key pair s=$s, u=$u")
        }

        return chain.proceed(request).descramble(descrambler)
    }
}

private fun Response.descramble(descrambler: SpeedBinbDescrambler): Response {
    if (!isSuccessful || !descrambler.isScrambled()) {
        return this
    }

    val image = BitmapFactory.decodeStream(this.body.byteStream())
    val descrambled = descrambler.descrambleImage(image)
    image.recycle()

    val buffer = Buffer()
    descrambled.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
    descrambled.recycle()

    return newBuilder()
        .body(buffer.asResponseBody(JPEG_MEDIA_TYPE, buffer.size))
        .build()
}

private const val NUMERIC_CHARACTERS = "0123456789"
private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
