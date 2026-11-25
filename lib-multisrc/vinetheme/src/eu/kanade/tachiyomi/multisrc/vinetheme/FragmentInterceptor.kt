package eu.kanade.tachiyomi.multisrc.vinetheme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException

class FragmentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        if (fragment?.startsWith("{") != true) return chain.proceed(request)

        val layout = fragment.parseAs<PageLayout>()
        val page = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        var response = chain.proceed(request)
        layout.parts.forEachIndexed { index, part ->
            if (index > 0) response = chain.proceed(request.newBuilder().url(part.url).build())
            if (!response.isSuccessful) return response
            val bitmap = response.body.byteStream().use(BitmapFactory::decodeStream)
                ?: throw IOException("Failed to decode image")
            // One pixel wider and taller: the site's layouts leave 1 px gaps between parts
            canvas.drawBitmap(bitmap, null, Rect(part.x, part.y, part.x + part.width + 1, part.y + part.height + 1), null)
        }

        val buffer = Buffer()
        page.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        return response.newBuilder()
            .request(request)
            .body(buffer.asResponseBody(JPEG))
            .build()
    }

    companion object {
        private val JPEG = "image/jpeg".toMediaType()
    }
}
