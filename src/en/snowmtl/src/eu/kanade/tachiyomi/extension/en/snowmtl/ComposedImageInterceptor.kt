package eu.kanade.tachiyomi.extension.en.snowmtl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import kotlin.math.min

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
        val defaultTextSize = 22.sp // arbitrary

        val textPaint = TextPaint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL_AND_STROKE
            textSize = defaultTextSize
            isAntiAlias = true
        }

        val spacingMultiplier = 1f
        val spacingAddition = 0f

        translation
            .filter { it.text.isNotBlank() }
            .forEach { caption ->
                val layout = StaticLayout.Builder.obtain(caption.text, 0, caption.text.length, textPaint, caption.width.toInt()).apply {
                    setAlignment(Layout.Alignment.ALIGN_CENTER)
                    setLineSpacing(spacingAddition, spacingMultiplier)
                    setIncludePad(false)
                }.build()

                val fontHeight = textPaint.fontMetrics.let { it.bottom - it.top }
                val dialogBoxLineCount = caption.height / fontHeight

                // Invert color in black dialog box and font scale. Change StaticLayout by reference
                textPaint.apply {
                    val pixelColor = bitmap.getPixel(caption.centerX.toInt(), caption.centerY.toInt())
                    val inverseColor = (Color.WHITE - pixelColor) or Color.BLACK
                    color = inverseColor
                    textSize = min(defaultTextSize * (dialogBoxLineCount / layout.lineCount), defaultTextSize)
                }

                // Centers text in y for captions smaller than the dialog box
                val y = when {
                    layout.lineCount < dialogBoxLineCount -> {
                        caption.centerY - layout.lineCount / 2f * fontHeight
                    }
                    else -> caption.y1
                }

                canvas.draw(layout, caption.x1, y)
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
