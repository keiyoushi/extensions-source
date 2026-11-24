package eu.kanade.tachiyomi.extension.en.thefarside

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

// The site shows a cartoon's caption as text under its image: draw it there, as in print
class CaptionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val caption = request.url.fragment ?: return chain.proceed(request)
        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val cartoon = response.body.byteStream().use { BitmapFactory.decodeStream(it) }!!
        val margin = cartoon.width / 20
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = cartoon.width / 28f
            typeface = Typeface.SERIF
        }
        val text = StaticLayout.Builder.obtain(caption, 0, caption.length, paint, cartoon.width - 2 * margin)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()

        val page = Bitmap.createBitmap(cartoon.width, cartoon.height + text.height + 2 * margin, Bitmap.Config.ARGB_8888)
        Canvas(page).apply {
            drawColor(Color.WHITE)
            drawBitmap(cartoon, 0f, 0f, null)
            translate(margin.toFloat(), (cartoon.height + margin).toFloat())
            text.draw(this)
        }
        cartoon.recycle()

        val output = Buffer()
        page.compress(Bitmap.CompressFormat.JPEG, 90, output.outputStream())
        page.recycle()
        return response.newBuilder()
            .body(output.asResponseBody("image/jpeg".toMediaType(), output.size))
            .build()
    }
}
