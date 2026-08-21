package eu.kanade.tachiyomi.extension.all.manhuarm.interceptors

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import eu.kanade.tachiyomi.extension.all.manhuarm.Dialog
import eu.kanade.tachiyomi.extension.all.manhuarm.Language
import eu.kanade.tachiyomi.extension.all.manhuarm.MARKER_REGEX
import eu.kanade.tachiyomi.extension.all.manhuarm.Manhuarm.Companion.PAGE_REGEX
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

// The Interceptor joins the dialogues and pages of the manga.
class ComposedImageInterceptor(
    private val languageProvider: () -> Language,
) : Interceptor {

    private val language: Language
        get() = languageProvider()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        if (PAGE_REGEX.containsMatchIn(url).not()) {
            return chain.proceed(request)
        }

        val dialogues = runCatching { request.url.fragment?.parseAs<List<Dialog>>() }
            .getOrNull()
            .orEmpty()

        if (dialogues.isEmpty()) {
            return chain.proceed(request)
        }

        val imageRequest = request.newBuilder()
            .url(url)
            .build()

        val response = chain.proceed(imageRequest)

        if (response.isSuccessful.not()) {
            return response
        }

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
            ?.copy(Bitmap.Config.ARGB_8888, true)

        if (bitmap == null) {
            response.close()
            return chain.proceed(
                request.newBuilder().url(url.substringBefore("#")).build(),
            )
        }

        val canvas = Canvas(bitmap)

        dialogues.forEach { dialog ->
            dialog.scale = language.dialogBoxScale
            val textPaint = createTextPaint(selectFontFamily())
            val dialogBox = createDialogBox(dialog, textPaint)
            canvas.draw(textPaint, dialogBox, dialog)
        }

        val output = ByteArrayOutputStream()

        val ext = url.substringBefore("#")
            .substringBefore("?")
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

    private fun createTextPaint(font: Typeface?): TextPaint {
        val defaultTextSize = language.fontSize.pt
        return TextPaint().apply {
            color = Color.BLACK
            textSize = defaultTextSize
            font?.let {
                typeface = it
            }
            isAntiAlias = true
        }
    }

    private val typefaceCache = ConcurrentHashMap<String, Typeface?>()

    private fun selectFontFamily(): Typeface? {
        if (language.disableFontSettings) {
            return null
        }
        return typefaceCache.computeIfAbsent(language.fontName) { loadFont("$it.ttf") }
    }

    /**
     * Loads font from the `assets/fonts` directory within the APK
     *
     * @param fontName The name of the font to load.
     * @return A `Typeface` instance of the loaded font or `null` if an error occurs.
     *
     * Example usage:
     * <pre>{@code
     *   val typeface: TypeFace? = loadFont("filename.ttf")
     * }</pre>
     */
    private fun loadFont(fontName: String): Typeface? = try {
        this::class.java.classLoader!!
            .getResourceAsStream("assets/fonts/$fontName")
            .toTypeface(fontName)
    } catch (_: Exception) {
        null
    }

    private fun InputStream.toTypeface(fontName: String): Typeface? {
        val fontFile = File.createTempFile(fontName, ".${fontName.substringAfter(".")}")
        try {
            this.copyTo(FileOutputStream(fontFile))
            return Typeface.createFromFile(fontFile)
        } finally {
            fontFile.delete()
        }
    }

    /**
     * Builds the text layout so that it always fits entirely inside the balloon,
     * both horizontally and vertically, with a small padding around the edges.
     */
    private fun createDialogBox(dialog: Dialog, textPaint: TextPaint): StaticLayout {
        var dialogBox = createBoxLayout(dialog, textPaint)
        val targetWidth = dialog.innerWidth()
        val targetHeight = dialog.innerHeight()

        while (dialogBox.height > targetHeight || hasLineOverflow(dialogBox, targetWidth)) {
            textPaint.textSize -= 0.5f
            if (textPaint.textSize <= MIN_TEXT_SIZE) break
            dialogBox = createBoxLayout(dialog, textPaint)
        }

        textPaint.color = Color.BLACK
        textPaint.bgColor = Color.WHITE

        return dialogBox
    }

    private fun hasLineOverflow(layout: StaticLayout, targetWidth: Float): Boolean {
        for (i in 0 until layout.lineCount) {
            if (layout.getLineWidth(i) > targetWidth + 1f) return true
        }
        return false
    }

    private fun createBoxLayout(dialog: Dialog, textPaint: TextPaint): StaticLayout {
        val text = dialog.getTextBy(language).cleanUp()

        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, max(1, dialog.innerWidth().toInt())).apply {
            setAlignment(Layout.Alignment.ALIGN_CENTER)
            setIncludePad(language.disableFontSettings)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (language.disableWordBreak) {
                    setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
                    setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                    return@apply
                }
                setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
                setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
            }
        }.build()
    }

    private fun String.cleanUp(): String = replace(HTML_TAG_REGEX, "").trim().replaceFirst(MARKER_REGEX, "").trim()

    private fun Dialog.padX(): Float = width * PAD_RATIO

    private fun Dialog.padY(): Float = height * PAD_RATIO

    private fun Dialog.innerWidth(): Float = width - 2f * padX()

    private fun Dialog.innerHeight(): Float = height - 2f * padY()

    private fun Canvas.draw(textPaint: TextPaint, layout: StaticLayout, dialog: Dialog) {
        save()
        translate(dialog.x + dialog.width / 2f, dialog.y + dialog.height / 2f)
        rotate(dialog.angle)
        translate(-layout.width / 2f, -layout.height / 2f)
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

        textPaint.strokeWidth = max(1.5f, textPaint.textSize * 0.06f)
        textPaint.color = textPaint.bgColor
        textPaint.style = Paint.Style.FILL_AND_STROKE

        layout.draw(this)

        textPaint.color = foregroundColor
        textPaint.style = style
    }

    // https://pixelsconverter.com/pt-to-px
    private val Int.pt: Float get() = this / SCALED_DENSITY

    companion object {
        // w3: Absolute Lengths [...](https://www.w3.org/TR/css3-values/#absolute-lengths)
        const val SCALED_DENSITY = 0.75f // 1px = 0.75pt
        val mediaType = "image/png".toMediaType()

        private const val PAD_RATIO = 0.06f
        private const val MIN_TEXT_SIZE = 6f
        private val HTML_TAG_REGEX = Regex("<[^>]*>")
    }
}
