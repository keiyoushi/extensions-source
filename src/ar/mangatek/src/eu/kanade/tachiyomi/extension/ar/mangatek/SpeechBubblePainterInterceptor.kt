package eu.kanade.tachiyomi.extension.ar.mangatek

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import eu.kanade.tachiyomi.extension.ar.mangatek.MangaTek.Companion.PAGE_REGEX
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

class SpeechBubblePainterInterceptor(baseUrl: () -> String, id: Long) : Interceptor {

    private val fontLoader = FontLoader(baseUrl, id)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        if (PAGE_REGEX.containsMatchIn(url).not() && request.url.fragment?.startsWith("[") != true) {
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

        val typefaces = speechBubbles.map { it.family() }.distinct()
            .associateWith { fontLoader.load(chain, it) }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        speechBubbles.forEach { bubble ->
            if (bubble.text.isEmpty()) return@forEach
            paint.typeface = typefaces.getValue(bubble.family())
            canvas.drawBubble(bubble, paint, bitmap.width.toFloat(), bitmap.height.toFloat())
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

    private fun Bubble.family() = fontFamily.ifBlank { DEFAULT_FAMILY }

    private fun Canvas.drawBubble(bubble: Bubble, paint: Paint, imageWidth: Float, imageHeight: Float) {
        val left = min(imageWidth - bubble.w, bubble.x).coerceAtLeast(0f)
        val top = min(imageHeight - bubble.h, bubble.y).coerceAtLeast(0f)
        val boxWidth = min(imageWidth - left, bubble.w)
        val boxHeight = min(imageHeight - top, bubble.h)
        if (boxWidth <= 0f || boxHeight <= 0f) return

        val lineHeight = bubble.lineHeight.takeIf { it > 0f } ?: DEFAULT_LINE_HEIGHT
        val maxWidth = max(1f, boxWidth - 2f)

        var size = max(1f, bubble.fontSizePx)
        var lines = wrapLines(bubble.text, paint, size, maxWidth)
        for (i in 0 until MAX_FIT_PASSES) {
            val scale = fitScale(lines, paint, maxWidth, boxHeight, lineHeight, size)
            if (scale >= 0.999f) break
            size = max(1f, size * scale)
            lines = wrapLines(bubble.text, paint, size, maxWidth)
        }

        val pitch = lineHeight * size
        val (align, x) = when (bubble.textAlign) {
            "left" -> Paint.Align.LEFT to 0f
            "right" -> Paint.Align.RIGHT to boxWidth
            else -> Paint.Align.CENTER to boxWidth / 2f
        }
        val baselineShift = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        val firstCenter = boxHeight / 2f - pitch * lines.size / 2f + pitch / 2f

        save()
        translate(left, top)
        rotate(bubble.angle, boxWidth / 2f, boxHeight / 2f)
        clipRect(0f, 0f, boxWidth, boxHeight)
        paint.textAlign = align

        if (!bubble.strokeColor.isNullOrBlank() && bubble.strokeWidthPx > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeMiter = 2f
            paint.strokeWidth = max(1f, bubble.strokeWidthPx)
            paint.color = parseColorSafe(bubble.strokeColor, Color.BLACK)
            lines.forEachIndexed { i, line ->
                drawText(line, x, firstCenter + i * pitch + baselineShift, paint)
            }
        }

        paint.style = Paint.Style.FILL
        paint.color = parseColorSafe(bubble.color, Color.BLACK)
        lines.forEachIndexed { i, line ->
            drawText(line, x, firstCenter + i * pitch + baselineShift, paint)
        }
        restore()
    }

    private fun wrapLines(text: String, paint: Paint, size: Float, maxWidth: Float): List<String> {
        paint.textSize = size
        val result = mutableListOf<String>()
        for (line in text.split("\n")) {
            if (paint.measureText(line) <= maxWidth * LINE_TOLERANCE || !line.contains(' ')) {
                result.add(line)
                continue
            }
            var current = ""
            for (word in line.split(" ")) {
                val candidate = if (current.isNotEmpty()) "$current $word" else word
                if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                    result.add(current)
                    current = word
                } else {
                    current = candidate
                }
            }
            if (current.isNotEmpty()) result.add(current)
        }
        return result
    }

    private fun fitScale(
        lines: List<String>,
        paint: Paint,
        maxWidth: Float,
        boxHeight: Float,
        lineHeight: Float,
        size: Float,
    ): Float {
        val widest = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        return minOf(
            1f,
            maxWidth / max(1f, widest),
            boxHeight / max(1f, lineHeight * size * lines.size),
        )
    }

    private fun parseColorSafe(color: String?, defaultColor: Int): Int = try {
        if (!color.isNullOrBlank()) Color.parseColor(color) else defaultColor
    } catch (_: Exception) {
        defaultColor
    }

    companion object {
        private val mediaType = "image/png".toMediaType()

        private const val DEFAULT_FAMILY = "Hacen Samra"
        private const val DEFAULT_LINE_HEIGHT = 1.2f
        private const val LINE_TOLERANCE = 1.15f
        private const val MAX_FIT_PASSES = 2
    }
}
