package eu.kanade.tachiyomi.extension.en.snowmtl

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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.io.IOException

// The Interceptor joins the captions and pages of the manga.
@RequiresApi(Build.VERSION_CODES.M)
class ComposedImageInterceptor(
    private val baseUrl: String,
) : Interceptor {

    private val json: Json by injectLazy()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        val isPageImageUrl = url.contains("storage.${baseUrl.substringAfterLast("/")}", true)
        if (isPageImageUrl.not()) {
            return chain.proceed(request)
        }

        val translation = request.url.fragment?.parseAs<List<Translation>>()
            ?: throw IOException("Translation not found")

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

        translation
            .filter { it.text.isNotBlank() }
            .forEach { caption ->
                val textPaint = createTextPaint()
                val dialogBox = createDialogBox(caption, textPaint, bitmap)
                val y = getAxiosY(textPaint, caption, dialogBox)
                canvas.draw(dialogBox, caption.x1, y)
            }

        val output = ByteArrayOutputStream()

        val format = when (url.substringAfterLast(".").lowercase()) {
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

    private fun createTextPaint(): TextPaint {
        val defaultTextSize = 22.sp // arbitrary

        return TextPaint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL_AND_STROKE
            textSize = defaultTextSize
            isAntiAlias = true
        }
    }

    /**
     * Adjust the text to the center of the dialog box when feasible.
     */
    private fun getAxiosY(textPaint: TextPaint, caption: Translation, dialogBox: StaticLayout): Float {
        val fontHeight = textPaint.fontMetrics.let { it.bottom - it.top }

        val dialogBoxLineCount = caption.height / fontHeight

        /**
         * Centers text in y for captions smaller than the dialog box
         */
        return when {
            dialogBox.lineCount < dialogBoxLineCount -> caption.centerY - dialogBox.lineCount / 2f * fontHeight
            else -> caption.y1
        }
    }

    private fun createDialogBox(caption: Translation, textPaint: TextPaint, bitmap: Bitmap): StaticLayout {
        var dialogBox = createBoxLayout(caption, textPaint)

        /**
         * The best way I've found to adjust the text in the dialog box (Especially in long dialogues)
         */
        while (dialogBox.height > caption.height) {
            textPaint.textSize -= 0.5f
            dialogBox = createBoxLayout(caption, textPaint)
        }

        textPaint.adjustTextColor(caption, bitmap)

        return dialogBox
    }

    private fun createBoxLayout(caption: Translation, textPaint: TextPaint) =
        StaticLayout.Builder.obtain(caption.text, 0, caption.text.length, textPaint, caption.width.toInt()).apply {
            setAlignment(Layout.Alignment.ALIGN_CENTER)
            setIncludePad(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
            }
        }.build()

    // Invert color in black dialog box.
    private fun TextPaint.adjustTextColor(caption: Translation, bitmap: Bitmap) {
        val pixelColor = bitmap.getPixel(caption.centerX.toInt(), caption.centerY.toInt())
        val inverseColor = (Color.WHITE - pixelColor) or Color.BLACK
        color = inverseColor
    }

    private inline fun <reified T> String.parseAs(): T {
        return json.decodeFromString(this)
    }

    private fun Canvas.draw(layout: StaticLayout, x: Float, y: Float) {
        save()
        translate(x, y)
        layout.draw(this)
        restore()
    }

    private val Int.sp: Float get() = this * SCALED_DENSITY

    companion object {
        const val SCALED_DENSITY = 1.5f // arbitrary
        val mediaType = "image/png".toMediaType()
    }
}
