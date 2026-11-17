package eu.kanade.tachiyomi.extension.all.lunaranime

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.util.Base64
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.IOException

// Draws the page's veil patches over the banners burned into the page, as the site's reader does
class VeilInterceptor(private val cdnHost: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != cdnHost && request.url.fragment == null) return chain.proceed(request)

        val veil = request.url.fragment?.toHttpUrlOrNull()?.let { url ->
            chain.proceedWithRetry(request.newBuilder().url(url).build(), VEIL_RETRIES).use {
                if (it.isSuccessful) it.asJsoup(Parser.xmlParser()).selectFirst("svg") else null
            }
        }

        val response = chain.proceedWithRetry(request, PAGE_RETRIES)
        if (veil == null || !response.isSuccessful) return response

        val page = response.use {
            BitmapFactory.decodeStream(it.body.byteStream(), null, BitmapFactory.Options().apply { inMutable = true })
        } ?: throw IOException("Failed to decode page")

        page.drawVeil(veil)

        val buffer = Buffer()
        page.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        page.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(JPEG_MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun Bitmap.drawVeil(veil: Element) {
        val scaleX = width / veil.floatAttr("width")
        val scaleY = height / veil.floatAttr("height")
        val canvas = Canvas(this)
        for (patch in veil.select("image")) {
            val bytes = Base64.decode(patch.attr("xlink:href").substringAfter("base64,"), Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IOException("Failed to decode page veil")
            val left = patch.floatAttr("x")
            val top = patch.floatAttr("y")
            val right = left + patch.floatAttr("width")
            val bottom = top + patch.floatAttr("height")
            canvas.drawBitmap(bitmap, null, RectF(left * scaleX, top * scaleY, right * scaleX, bottom * scaleY), null)
            bitmap.recycle()
        }
    }

    private fun Element.floatAttr(name: String): Float = attr(name).toFloatOrNull() ?: throw IOException("Failed to parse page veil")

    // The vault answers 503 while it prepares a file; the site's reader retries after 600 ms, 1200 ms, ...
    private fun Interceptor.Chain.proceedWithRetry(request: Request, retries: Int): Response {
        var response = proceed(request)
        repeat(retries) { attempt ->
            if (response.code != 503 || call().isCanceled()) return response
            response.close()
            Thread.sleep(RETRY_STEP_MS * (attempt + 1))
            response = proceed(request)
        }
        return response
    }

    companion object {
        private const val PAGE_RETRIES = 4
        private const val VEIL_RETRIES = 3
        private const val RETRY_STEP_MS = 600L
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
