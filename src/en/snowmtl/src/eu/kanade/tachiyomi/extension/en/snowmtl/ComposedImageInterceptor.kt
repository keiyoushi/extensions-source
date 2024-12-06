package eu.kanade.tachiyomi.extension.en.snowmtl

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
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.pow
import kotlin.math.sqrt

// The Interceptor joins the captions and pages of the manga.
@RequiresApi(Build.VERSION_CODES.O)
class ComposedImageInterceptor(
    private val baseUrl: String,
    private val client: OkHttpClient,
) : Interceptor {

    private val json: Json by injectLazy()

    private val fontFamily: MutableMap<String, Pair<String, Typeface?>> = mutableMapOf(
        "sub" to Pair<String, Typeface?>("$baseUrl/images/sub.ttf", null),
        "sfx" to Pair<String, Typeface?>("$baseUrl/images/sfx.ttf", null),
        "normal" to Pair<String, Typeface?>("$baseUrl/images/normal.ttf", null),
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        val isPageImageUrl = url.contains("${baseUrl.substringAfterLast("/")}/storage/", true)
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

        loadAllFont(chain)

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())!!
            .copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(bitmap)

        translation
            .filter { it.text.isNotBlank() }
            .forEach { caption ->
                val textPaint = createTextPaint(selectFontFamily(caption.type))
                val dialogBox = createDialogBox(caption, textPaint, bitmap)
                val y = getYAxis(textPaint, caption, dialogBox)
                canvas.draw(dialogBox, caption, caption.x1, y)
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

    private fun createTextPaint(font: Typeface?): TextPaint {
        val defaultTextSize = 50.sp // arbitrary
        return TextPaint().apply {
            color = Color.BLACK
            textSize = defaultTextSize
            font?.let {
                typeface = it
            }
            isAntiAlias = true
        }
    }

    private fun selectFontFamily(type: String): Typeface? {
        if (type in fontFamily) {
            return fontFamily[type]?.second
        }

        return when (type) {
            "inside", "outside" -> fontFamily["sfx"]?.second
            else -> fontFamily["normal"]?.second
        }
    }

    private fun loadAllFont(chain: Interceptor.Chain) {
        fontFamily.keys.forEach { key ->
            val font = fontFamily[key] ?: return@forEach
            if (font.second != null) {
                return@forEach
            }
            fontFamily[key] = key to (loadRemoteFont(font.first, chain) ?: loadFont("coming_soon_regular.ttf"))
        }
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
    private fun loadFont(fontName: String): Typeface? {
        return try {
            this::class.java.classLoader!!
                .getResourceAsStream("assets/fonts/$fontName")
                .toTypeface(fontName)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Loads a remote font and converts it into a usable font object.
     *
     * This function makes an HTTP request to download a font from a specified remote URL.
     * It then converts the response into a usable font object.
     */
    private fun loadRemoteFont(fontUrl: String, chain: Interceptor.Chain): Typeface? {
        return try {
            val request = GET(fontUrl, chain.request().headers)
            val response = client
                .newCall(request).execute()
                .takeIf(Response::isSuccessful) ?: return null
            val fontName = request.url.pathSegments.last()
            response.body.byteStream().toTypeface(fontName)
        } catch (e: Exception) {
            null
        }
    }

    private fun InputStream.toTypeface(fontName: String): Typeface? {
        val fontFile = File.createTempFile(fontName, fontName.substringAfter("."))
        this.copyTo(FileOutputStream(fontFile))
        return Typeface.createFromFile(fontFile)
    }

    /**
     * Adjust the text to the center of the dialog box when feasible.
     */
    private fun getYAxis(textPaint: TextPaint, caption: Translation, dialogBox: StaticLayout): Float {
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

        // Use source setup
        if (caption.isNewApi) {
            textPaint.color = caption.foregroundColor
            textPaint.bgColor = caption.backgroundColor
            textPaint.style = if (caption.isBold) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
        }

        /**
         * Forces font color correction if the background color of the dialog box and the font color are too similar.
         * It's a source configuration problem.
         */
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

        val minDistance = 80f // arbitrary
        if (colorDistance(pixelColor, caption.foregroundColor) > minDistance) {
            return
        }
        color = inverseColor
    }

    private inline fun <reified T> String.parseAs(): T {
        return json.decodeFromString(this)
    }

    private fun Canvas.draw(layout: StaticLayout, caption: Translation, x: Float, y: Float) {
        save()
        translate(x, y)
        rotate(caption.angle)
        layout.draw(this)
        restore()
    }

    private val Int.sp: Float get() = this * SCALED_DENSITY

    // ============================= Utils ======================================

    /**
     * Calculates the Euclidean distance between two colors in RGB space.
     *
     * This function takes two integer values representing hexadecimal colors,
     * converts them to their RGB components, and calculates the Euclidean distance
     * between the two colors. The distance provides a measure of how similar or
     * different the two colors are.
     *
     */
    private fun colorDistance(colorA: Int, colorB: Int): Double {
        val a = Color.valueOf(colorA)
        val b = Color.valueOf(colorB)

        return sqrt(
            (b.red() - a.red()).toDouble().pow(2) +
                (b.green() - a.green()).toDouble().pow(2) +
                (b.blue() - a.blue()).toDouble().pow(2),
        )
    }

    companion object {
        const val SCALED_DENSITY = 1.5f // arbitrary
        val mediaType = "image/png".toMediaType()
    }
}
