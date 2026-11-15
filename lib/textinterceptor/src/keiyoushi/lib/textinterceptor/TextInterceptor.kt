package keiyoushi.lib.textinterceptor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.Html
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.File

// Designer values:
private const val WIDTH: Int = 1000
private const val X_PADDING: Float = 50f
private const val Y_PADDING: Float = 25f
private const val HEADING_FONT_SIZE: Float = 36f
private const val BODY_FONT_SIZE: Float = 30f
private const val SPACING_MULT: Float = 1.1f
private const val SPACING_ADD: Float = 2f

// No need to touch this one:
private const val HOST = TextInterceptorHelper.HOST

/**
 * Draws the text pages made with [TextInterceptorHelper.createUrl] as images.
 *
 * @param fallbackFont path of a font in the extension's APK (e.g. `/assets/fonts/font.ttf`), used for the characters
 * that the device's fonts can't draw.
 */
class TextInterceptor(private val fallbackFont: String? = null) : Interceptor {

    // A font that fails to load leaves the pages drawn with the device's fonts only
    private val fallbackTypeface: Typeface? by lazy {
        val path = fallbackFont ?: return@lazy null
        runCatching {
            val file = File.createTempFile("font", ".ttf")
            try {
                javaClass.getResourceAsStream(path)!!.use { input -> file.outputStream().use(input::copyTo) }
                Typeface.createFromFile(file)
            } finally {
                file.delete()
            }
        }.getOrNull()
    }

    // With help from:
    // https://github.com/tachiyomiorg/tachiyomi-extensions/pull/13304#issuecomment-1234532897
    // https://medium.com/over-engineering/drawing-multiline-text-to-canvas-on-android-9b98f0bfa16a
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != HOST) return chain.proceed(request)

        val heading = url.pathSegments[0].takeIf { it.isNotEmpty() }?.let {
            val title = textFixer(url.pathSegments[0])

            // Heading
            val paintHeading = TextPaint().apply {
                color = Color.BLACK
                textSize = HEADING_FONT_SIZE
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            @Suppress("DEPRECATION")
            StaticLayout(
                title.withFallbackFont(paintHeading),
                paintHeading,
                (WIDTH - 2 * X_PADDING).toInt(),
                Layout.Alignment.ALIGN_NORMAL,
                SPACING_MULT,
                SPACING_ADD,
                true,
            )
        }

        val body = url.pathSegments[1].takeIf { it.isNotEmpty() }?.let {
            val story = textFixer(it)

            // Body
            val paintBody = TextPaint().apply {
                color = Color.BLACK
                textSize = BODY_FONT_SIZE
                typeface = Typeface.DEFAULT
                isAntiAlias = true
            }

            @Suppress("DEPRECATION")
            StaticLayout(
                story.withFallbackFont(paintBody),
                paintBody,
                (WIDTH - 2 * X_PADDING).toInt(),
                Layout.Alignment.ALIGN_NORMAL,
                SPACING_MULT,
                SPACING_ADD,
                true,
            )
        }

        // Image building
        val headingHeight = heading?.height ?: 0
        val bodyHeight = body?.height ?: 0
        val imgHeight: Int = (headingHeight + bodyHeight + 2 * Y_PADDING).toInt()
        val bitmap: Bitmap = Bitmap.createBitmap(WIDTH, imgHeight, Bitmap.Config.ARGB_8888)

        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            heading?.draw(this, X_PADDING, Y_PADDING)
            body?.draw(this, X_PADDING, Y_PADDING + headingHeight.toFloat())
        }

        // Image converting & returning
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream)
        val responseBody = stream.toByteArray().toResponseBody("image/png".toMediaType())
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()
    }

    private fun textFixer(htmlString: String): String = Html.fromHtml(htmlString, Html.FROM_HTML_MODE_LEGACY).toString()

    private fun String.withFallbackFont(paint: TextPaint): CharSequence {
        val font = fallbackTypeface ?: return this
        val fallbackPaint = TextPaint(paint).apply { typeface = font }
        val text = SpannableString(this)
        var start = 0
        while (start < length) {
            val end = offsetByCodePoints(start, 1)
            val char = substring(start, end)
            if (!paint.hasGlyph(char) && fallbackPaint.hasGlyph(char)) {
                text.setSpan(FontSpan(font), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            start = end
        }
        return text
    }

    @Suppress("SameParameterValue")
    private fun StaticLayout.draw(canvas: Canvas, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        this.draw(canvas)
        canvas.restore()
    }

    private class FontSpan(private val font: Typeface) : MetricAffectingSpan() {
        override fun updateMeasureState(paint: TextPaint) {
            paint.typeface = font
        }

        override fun updateDrawState(paint: TextPaint) {
            paint.typeface = font
        }
    }
}

object TextInterceptorHelper {

    const val HOST = "tachiyomi-lib-textinterceptor"

    fun createUrl(title: String, text: String): String = "http://$HOST/" + Uri.encode(title) + "/" + Uri.encode(text)
}
