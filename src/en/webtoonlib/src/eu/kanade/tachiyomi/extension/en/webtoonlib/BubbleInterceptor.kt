package eu.kanade.tachiyomi.extension.en.webtoonlib

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import keiyoushi.lib.imagedecoder.ImageDecoder
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import kotlin.math.max
import kotlin.math.min

/** Letters softsub pages whose original images are raws, sized like the site's reader */
class BubbleInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment ?: return chain.proceed(request)
        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val decoded = ImageDecoder.decode(response.use { it.body.bytes() })
        val page = decoded.copy(Bitmap.Config.ARGB_8888, true)
        decoded.recycle()
        val canvas = Canvas(page)
        fragment.parseAs<List<Bubble>>().forEach { it.draw(canvas, page.width, page.height) }

        val image = Buffer()
        page.compress(Bitmap.CompressFormat.JPEG, 90, image.outputStream())
        page.recycle()
        return response.newBuilder()
            .body(image.asResponseBody(JPEG, image.size))
            .build()
    }

    private fun Bubble.draw(canvas: Canvas, pageWidth: Int, pageHeight: Int) {
        val scale = pageWidth / READER_WIDTH
        val boxWidth = (width / 100 * pageWidth).toInt()
        val boxHeight = height / 100 * pageHeight
        val left = x / 100 * pageWidth
        val top = y / 100 * pageHeight

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, typefaceStyle)
            letterSpacing = (this@draw.letterSpacing ?: 0f) / 100
        }
        val base = fontSize ?: 16f
        val maxSize = max(8f, min(base * scale, 2 * base))
        val layout = FIT_STEPS.firstNotNullOfOrNull { fit ->
            layoutAt(paint, max(10f, maxSize * fit), boxWidth).takeIf { it.height <= boxHeight }
        } ?: layoutAt(paint, max(10f, maxSize * FIT_STEPS.last()), boxWidth)

        canvas.save()
        canvas.rotate(rotation ?: 0f, left + boxWidth / 2, top + boxHeight / 2)
        canvas.translate(left, top + (boxHeight - layout.height) / 2)
        strokeWidth?.takeIf { it > 0 }?.let { stroke ->
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * max(1f, stroke * scale)
            paint.color = strokeColor.toColor()
            layout.draw(canvas)
            paint.style = Paint.Style.FILL
        }
        paint.color = color.toColor()
        layout.draw(canvas)
        canvas.restore()
    }

    private fun Bubble.layoutAt(paint: TextPaint, size: Float, width: Int): StaticLayout {
        paint.textSize = size
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(size * (lineHeight ?: 1.2f) - paint.fontSpacing, 1f)
            .setIncludePad(false)
            .build()
    }

    private fun String?.toColor(): Int = runCatching { Color.parseColor(this) }.getOrDefault(Color.BLACK)

    private val Bubble.alignment
        get() = when (textAlign) {
            "left" -> Layout.Alignment.ALIGN_NORMAL
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }

    private val Bubble.typefaceStyle
        get() = when {
            bold == true && italic == true -> Typeface.BOLD_ITALIC
            bold == true -> Typeface.BOLD
            italic == true -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
}

private val JPEG = "image/jpeg".toMediaType()
private const val READER_WIDTH = 800f

// The site's reader shrinks text down to 60% to fit its box
private val FIT_STEPS = listOf(1f, 0.95f, 0.9f, 0.85f, 0.8f, 0.75f, 0.7f, 0.65f, 0.6f)
