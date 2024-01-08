package eu.kanade.tachiyomi.extension.th.doodmanga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

object ScrambledImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url
        val rawSovleImage = url.queryParameter("sovleImage") ?: return response
        val sovleImage = rawSovleImage.split("::").map { numbers ->
            val (x, y, px, py) = numbers.split(",")
            listOf(x, y, px, py)
        }

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val width = bitmap.width
        val height = bitmap.height

        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)

        sovleImage.forEach { (x, y, px, py) ->
            val segmentX = x.toFloat()
            val segmentY = y.toFloat()
            val positionX = px.substringBefore(".").toInt()
            val positionY = py.substringBefore(".").toInt()

            val subBitmap = Bitmap.createBitmap(bitmap, positionX, positionY, request.url.queryParameter("segmentWidth")!!.toInt(), request.url.queryParameter("segmentHeight")!!.toInt())
            canvas.drawBitmap(subBitmap, segmentX, segmentY, null)
            subBitmap.recycle()
        }

        val output = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 90, output)

        return response.newBuilder()
            .body(output.toByteArray().toResponseBody(response.body.contentType()))
            .build()
    }
}
