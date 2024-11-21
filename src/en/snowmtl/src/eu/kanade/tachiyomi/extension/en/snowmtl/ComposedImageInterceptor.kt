package eu.kanade.tachiyomi.extension.en.snowmtl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.absoluteValue

// The Interceptor joins the captions and pages of the manga.
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

        val paint = Paint().apply {
            textAlign = Paint.Align.LEFT
            color = Color.BLACK
            style = Paint.Style.FILL_AND_STROKE
            textSize = defaultTextSize
            isAntiAlias = true
            typeface = Typeface.SANS_SERIF
        }

        val textMarginTop = 20 // arbitrary
        val textMarginLeft = 40 // arbitrary
        val spaceBetween = 2 // arbitrary
        val defaultFontScale = 1f

        translation
            .filter { it.text.isNotBlank() }
            .forEach {
                var charWidth = paint.getCharWidth()
                var charHeight = paint.getCharHeight()

                var lines = it.breakLines(charWidth)
                /*
                    Reduces the font according to the size of the line in the dialog box. (space between applied)
                        Ex. 1:
                            - Box: 10 lines
                            - Text: 9 lines
                            - Scale: 1
                        Ex. 2:
                            - Box: 10 lines
                            - Text: 15
                            - Scale: 0.6 (10/ (15 + 1)) // 1 extra line(arbitrary)

                        Ex. 3:
                            - Box: 10 lines
                            - Text: 2 line
                            - Scale: 1
                 */
                val dialogBoxLines = it.height / charHeight
                val fontScale = when {
                    lines.size >= dialogBoxLines -> dialogBoxLines / (lines.size + 1)
                    else -> defaultFontScale
                }

                // Use font scale in large dialogs
                if (fontScale != defaultFontScale) {
                    paint.apply {
                        this.textSize = defaultTextSize * fontScale
                    }
                    // reprocessing break lines
                    charWidth = paint.getCharWidth()
                    charHeight = paint.getCharHeight()
                    lines = it.breakLines(charWidth)
                }

                // Centers the text if it is smaller than half of the dialog box.
                val isHalfTheBox = lines.size / dialogBoxLines < 0.5
                val initialY = when {
                    isHalfTheBox -> it.centerY - lines.size * charHeight / 2
                    else -> it.y1.toFloat()
                }

                // Invert color in black dialog box
                paint.apply {
                    val pixelColor = bitmap.getPixel(it.centerX.toInt(), it.centerY.toInt())
                    val inverseColor = (Color.WHITE - pixelColor) or Color.BLACK
                    color = inverseColor
                }

                lines.forEachIndexed { index, line ->
                    // Centers the text on the X axis and positions it inside the dialog box
                    val x = (it.centerX - (line.length * charWidth / 2)).absoluteValue + textMarginLeft

                    // Positions the text inside the dialog box on the Y axis
                    val y = (initialY + charHeight * index * spaceBetween).absoluteValue + textMarginTop

                    canvas.drawText(line, 0, line.length, x, y, paint)
                }
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

    val Int.sp: Float get() = this * scaledDensity

    companion object {
        const val scaledDensity = 1.5f // arbitrary
        val mediaType = "image/png".toMediaType()
    }
}

/**
 * Gets the pixel width of the font character, used to calculate the
 * scale needed to apply to the font given the size of the dialog box
 */
fun Paint.getCharWidth(): Float {
    val text = "A" // Just any character to get the size of the character box
    val fontWidth = FloatArray(1)
    getTextWidths(text.first().toString(), fontWidth)
    return fontWidth.first()
}

/**
 * Gets the pixel height of the font character, used to calculate
 * line breaks in the text, given the maximum amount supported
 * in the dialog box.
 */
fun Paint.getCharHeight(): Float {
    val text = "A" // Just any character to get the size of the character box
    val bounds = Rect()
    getTextBounds(text, 0, text.length, bounds)
    return bounds.height().toFloat()
}
