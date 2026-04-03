package keiyoushi.lib.publus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.nio.charset.StandardCharsets

class PublusInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url

        if (url.fragment.isNullOrEmpty() || !response.isSuccessful) {
            return response
        }

        val params = try {
            val fragmentJson = String(
                Base64.decode(url.fragment, Base64.URL_SAFE),
                StandardCharsets.UTF_8,
            )
            fragmentJson.parseAs<PublusFragment>()
        } catch (_: Exception) {
            return response
        }

        if (!params.s) {
            return response
        }

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val unscrambled = if (params.k1.isEmpty() || params.bw == 0) {
            val patternStr = "${params.file}/${params.no}"
            val pattern = (patternStr.sumOf { it.code } % 4) + 1
            PublusImage.unscrambleNoKeys(bitmap, pattern)
        } else {
            val keys = listOf(
                params.k1.toIntArray(),
                params.k2.toIntArray(),
                params.k3.toIntArray(),
            )

            val attributes = PublusPageAttributes(
                no = params.no,
                ns = params.ns,
                ps = params.ps,
                rs = params.rs,
                blockWidth = params.bw,
                blockHeight = params.bh,
                contentWidth = params.cw,
                contentHeight = params.ch,
            )

            PublusImage.unscramble(bitmap, attributes, keys, params.file)
        }

        bitmap.recycle()
        val buffer = Buffer()
        unscrambled.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        unscrambled.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody("image/jpeg".toMediaType(), buffer.size))
            .build()
    }
}
