package eu.kanade.tachiyomi.extension.ar.mangatek

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.extension.ar.mangatek.MangaTek.Companion.PAGE_REGEX
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream

@RequiresApi(Build.VERSION_CODES.O)
class SpeechBubblePainterInterceptor(val fontSize: Int) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        if (PAGE_REGEX.containsMatchIn(url).not()) {
            return chain.proceed(request)
        }

        val speechBubbles = request.url.fragment?.parseAs<List<Bubble>>()
            ?: emptyList()

        val imageRequest = request.newBuilder()
            .url(url)
            .build()

        val response = chain.proceed(imageRequest)

        if (response.isSuccessful.not()) {
            return response
        }

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())!!
            .copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(bitmap)

        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        speechBubbles.forEach { speechBubble ->
            val pxX = (speechBubble.left / 100f) * imageWidth
            val pxY = (speechBubble.top / 100f) * imageHeight
            val pxWidth = (speechBubble.width / 100f) * imageWidth
            val pxHeight = (speechBubble.height / 100f) * imageHeight
            val pxCenterY = pxY + (pxHeight / 2f)

            val textPaint = createTextPaint(fontSize)
            val bubble = createBubble(pxHeight, pxWidth, speechBubble, textPaint)
            val finalY = getYAxis(pxY, pxHeight, pxCenterY, textPaint, bubble)
            canvas.draw(textPaint, bubble, speechBubble.angle, pxX, finalY)
        }

        val output = ByteArrayOutputStream()

        val ext = url.substringBefore("#")
            .substringAfterLast(".")
            .lowercase()
        val format = when (ext) {
            "png" -> Bitmap.CompressFormat.PNG
            "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG
            else -> Bitmap.CompressFormat.WEBP
        }

        bitmap.compress(format, 100, output)

        val responseBody = output.toByteArray().toResponseBody(mediaType)

        return response.newBuilder()
            .body(responseBody)
            .build()
    }

    private fun createTextPaint(fontSize: Int): TextPaint {
        val defaultTextSize = fontSize.pt
        return TextPaint().apply {
            color = Color.BLACK
            textSize = defaultTextSize
            isAntiAlias = true
        }
    }

    private fun getYAxis(
        pxY: Float,
        pxHeight: Float,
        pxCenterY: Float,
        textPaint: TextPaint,
        bubble: StaticLayout,
    ): Float {
        val fontHeight = textPaint.fontMetrics.let { it.bottom - it.top }
        val dialogBoxLineCount = pxHeight / fontHeight
        return when {
            bubble.lineCount < dialogBoxLineCount -> pxCenterY - (bubble.lineCount / 2f) * fontHeight
            else -> pxY
        }
    }

    private fun createBubble(
        pxHeight: Float,
        pxWidth: Float,
        dialog: Bubble,
        textPaint: TextPaint,
    ): StaticLayout {
        var bubble = createBubbleLayout(pxWidth, dialog, textPaint)
        while (bubble.height > pxHeight) {
            textPaint.textSize -= 0.5f
            bubble = createBubbleLayout(pxWidth, dialog, textPaint)
        }

        textPaint.color = Color.BLACK
        textPaint.bgColor = Color.WHITE

        return bubble
    }

    private fun createBubbleLayout(pxWidth: Float, dialog: Bubble, textPaint: TextPaint): StaticLayout {
        val text = dialog.text.cleanUp()

        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, pxWidth.toInt()).apply {
            setAlignment(Layout.Alignment.ALIGN_CENTER)
            setIncludePad(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
                setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
            }
        }.build()
    }

    private fun String.cleanUp(): String = Jsoup.parse(this).text()

    private fun Canvas.draw(textPaint: TextPaint, layout: StaticLayout, angle: Float, x: Float, y: Float) {
        save()
        translate(x, y)
        rotate(angle)
        drawTextOutline(textPaint, layout)
        drawText(textPaint, layout)
        restore()
    }

    private fun Canvas.drawText(textPaint: TextPaint, layout: StaticLayout) {
        textPaint.style = Paint.Style.FILL
        layout.draw(this)
    }

    private fun Canvas.drawTextOutline(textPaint: TextPaint, layout: StaticLayout) {
        val foregroundColor = textPaint.color
        val style = textPaint.style

        textPaint.strokeWidth = 5F
        textPaint.color = textPaint.bgColor
        textPaint.style = Paint.Style.FILL_AND_STROKE

        layout.draw(this)

        textPaint.color = foregroundColor
        textPaint.style = style
    }

    private val Int.pt: Float get() = this / SCALED_DENSITY

    companion object {
        const val SCALED_DENSITY = 0.75f // 1px = 0.75pt
        val mediaType = "image/png".toMediaType()
    }
}
