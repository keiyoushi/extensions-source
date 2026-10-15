package eu.kanade.tachiyomi.extension.ja.mangatoshokanz

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

@Serializable
internal class ViewerDoc(
    @SerialName("Location") val location: Location,
    @SerialName("Orders") val orders: List<Order>,
) {
    @Serializable
    internal class Location(
        val base: String,
        @SerialName("scramble_dir") val scrambleDir: String,
    )

    @Serializable
    internal class Order(
        val no: Int,
        val name: String,
        val scramble: Scramble,
    )

    @Serializable
    internal class Scramble(
        val w: Int,
        val h: Int,
        val crops: List<Crop>,
    ) {
        // w,h;x,y,x2,y2,w,h;...
        fun encode() = buildString {
            append(w, ",", h)
            crops.forEach { append(";", it.x, ",", it.y, ",", it.x2, ",", it.y2, ",", it.w, ",", it.h) }
        }
    }

    @Serializable
    internal class Crop(
        val x: Int,
        val y: Int,
        val x2: Int,
        val y2: Int,
        val w: Int,
        val h: Int,
    )
}

// Images are served as a strip of shuffled tiles; each crop copies (x2, y2) of the strip to (x, y) of the page
internal class DescrambleInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        val response = chain.proceed(request)

        if (fragment.isNullOrEmpty() || !response.isSuccessful) return response

        val parts = fragment.split(";").map { part -> part.split(",").map(String::toInt) }
        val (width, height) = parts.first()

        val source = BitmapFactory.decodeStream(response.body.byteStream())
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        parts.drop(1).forEach { (x, y, x2, y2, w, h) ->
            canvas.drawBitmap(source, Rect(x2, y2, x2 + w, y2 + h), Rect(x, y, x + w, y + h), null)
        }
        source.recycle()

        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    private operator fun <T> List<T>.component6() = this[5]

    companion object {
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
