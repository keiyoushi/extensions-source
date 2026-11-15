package keiyoushi.lib.speedbinb

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import keiyoushi.lib.speedbinb.descrambler.PtBinbDescramblerA
import keiyoushi.lib.speedbinb.descrambler.PtBinbDescramblerF
import keiyoushi.lib.speedbinb.descrambler.PtImgDescrambler
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException

class SpeedBinbInterceptor : Interceptor {
    private val textInterceptor by lazy { TextInterceptor() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host == TextInterceptorHelper.HOST) {
            return textInterceptor.intercept(chain)
        }

        val response = chain.proceed(request)
        val fragment = request.url.fragment
        if (!response.isSuccessful) return response

        val (image, descrambler) = when {
            request.url.pathSegments.last().endsWith(".ptimg.json") -> {
                val metadata = response.parseAs<PtImg>()
                val imageUrl = request.url.newBuilder()
                    .setPathSegment(request.url.pathSize - 1, metadata.resources.i.src)
                    .build()
                chain.proceed(request.newBuilder().url(imageUrl).build()) to PtImgDescrambler(metadata)
            }

            fragment == null || !fragment.startsWith("ptbinb,") -> return response

            else -> {
                val (s, u) = fragment.removePrefix("ptbinb,").split(",", limit = 2)
                val descrambler = when {
                    s.isEmpty() && u.isEmpty() -> return response
                    s[0] == '=' && u[0] == '=' -> PtBinbDescramblerF(s, u)
                    s[0] in '0'..'9' && u[0] in '0'..'9' -> PtBinbDescramblerA(s, u)
                    else -> {
                        response.close()
                        throw IOException("Cannot select descrambler for key pair s=$s, u=$u")
                    }
                }
                response to descrambler
            }
        }

        if (!image.isSuccessful || !descrambler.isScrambled()) return image

        val bitmap = BitmapFactory.decodeStream(image.body.byteStream())
        val descrambled = descrambler.descrambleImage(bitmap)
        bitmap.recycle()

        val buffer = Buffer()
        descrambled.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        descrambled.recycle()

        return image.newBuilder()
            .body(buffer.asResponseBody(JPEG_MEDIA_TYPE, buffer.size))
            .build()
    }
}

private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
