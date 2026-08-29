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
import eu.kanade.tachiyomi.extension.ar.mangatek.MangaTek.Companion.PAGE_REGEX
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class SpeechBubblePainterInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        if (PAGE_REGEX.containsMatchIn(url).not()) {
            return chain.proceed(request)
        }

        val speechBubbles = request.url.fragment?.parseAs<List<Bubble>>().orEmpty()

        val response = chain.proceed(request.newBuilder().url(url).build())
        if (!response.isSuccessful || speechBubbles.isEmpty()) {
            return response
        }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream(), null, options)!!

        val canvas = Canvas(bitmap)

        val textPaint = TextPaint().apply {
            isAntiAlias = true
        }
        speechBubbles.forEach { speechBubble ->
            val pxX = speechBubble.x
            val pxY = speechBubble.y
            val pxWidth = speechBubble.w
            val pxHeight = speechBubble.h
            val pxCenterY = pxY + (pxHeight / 2f)

            textPaint.color = Color.parseColor(speechBubble.color)
            textPaint.bgColor = Color.parseColor(speechBubble.strokeColor)
            textPaint.textSize = speechBubble.fontSizePx
            textPaint.strokeWidth = speechBubble.strokeWidthPx

            val bubble = createBubble(pxHeight, pxWidth, speechBubble, textPaint)
            val finalY = getYAxis(pxY, pxHeight, pxCenterY, textPaint, bubble)
            canvas.draw(textPaint, bubble, speechBubble.angle, pxX, finalY)
        }

        val ext = url.substringBefore("#")
            .substringBefore("?")
            .substringAfterLast(".")
            .lowercase()
        val format = when (ext) {
            "png" -> Bitmap.CompressFormat.PNG
            "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }

        val output = ByteArrayOutputStream().use { stream ->
            bitmap.compress(format, 100, stream)
            stream.toByteArray()
        }

        bitmap.recycle()

        return response.newBuilder()
            .body(output.toResponseBody(mediaType))
            .build()
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

        if (bubble.height <= pxHeight) {
            return bubble
        }

        while (bubble.height > pxHeight) {
            textPaint.textSize -= 0.5f
            bubble = createBubbleLayout(pxWidth, dialog, textPaint)
        }

        return bubble
    }

    private fun createBubbleLayout(pxWidth: Float, dialog: Bubble, textPaint: TextPaint): StaticLayout {
        val text = dialog.text

        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, pxWidth.toInt()).apply {
            setAlignment(Layout.Alignment.ALIGN_CENTER)
            setIncludePad(true)
            setLineSpacing(0f, dialog.lineHeight.coerceAtLeast(0.5f))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
                setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
            }
        }.build()
    }

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
        textPaint.strokeWidth = 0f
        layout.draw(this)
    }

    private fun Canvas.drawTextOutline(textPaint: TextPaint, layout: StaticLayout) {
        val foregroundColor = textPaint.color
        val style = textPaint.style

        textPaint.color = textPaint.bgColor
        textPaint.style = Paint.Style.FILL_AND_STROKE

        layout.draw(this)

        textPaint.color = foregroundColor
        textPaint.style = style
    }

    companion object {
        val mediaType = "image/png".toMediaType()
    }
}
