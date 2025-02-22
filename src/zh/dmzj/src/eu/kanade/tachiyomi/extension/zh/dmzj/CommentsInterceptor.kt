package eu.kanade.tachiyomi.extension.zh.dmzj

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

object CommentsInterceptor : Interceptor {

    class Tag

    private const val MAX_HEIGHT = 1920
    private const val WIDTH = 1080
    private const val UNIT = 32
    private const val UNIT_F = UNIT.toFloat()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.tag(Tag::class) == null) return response

        val comments = ApiV3.parseChapterComments(response, MAX_HEIGHT / (UNIT * 2))

        val paint = TextPaint().apply {
            color = Color.BLACK
            textSize = UNIT_F
            isAntiAlias = true
        }

        var height = UNIT
        val layouts = comments.map {
            @Suppress("DEPRECATION")
            StaticLayout(it, paint, WIDTH - 2 * UNIT, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
        }.takeWhile {
            val lineHeight = it.height + UNIT
            if (height + lineHeight <= MAX_HEIGHT) {
                height += lineHeight
                true
            } else {
                false
            }
        }

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)

        var y = UNIT
        for (layout in layouts) {
            canvas.save()
            canvas.translate(UNIT_F, y.toFloat())
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + UNIT
        }

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, output)
        val body = output.toByteArray().toResponseBody("image/png".toMediaType())
        return response.newBuilder().body(body).build()
    }
}
