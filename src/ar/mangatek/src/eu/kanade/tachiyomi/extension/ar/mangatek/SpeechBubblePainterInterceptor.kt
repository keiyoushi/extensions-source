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
import kotlin.math.max

@RequiresApi(Build.VERSION_CODES.O)
class SpeechBubblePainterInterceptor(val fontSize: Int, val enableDarkMode: Boolean = false) : Interceptor {

    private val startTime = System.currentTimeMillis()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        if (PAGE_REGEX.containsMatchIn(url).not()) {
            return chain.proceed(request)
        }

        PerformanceMonitor.startTimer()
        
        val speechBubbles = request.url.fragment?.parseAs<List<Bubble>>()
            ?: emptyList()

        val imageRequest = request.newBuilder()
            .url(url)
            .build()

        val response = chain.proceed(imageRequest)

        if (response.isSuccessful.not()) {
            LoggerService.warning("Failed to load image: ${response.code}")
            return response
        }

        try {
            val bitmap = BitmapFactory.decodeStream(response.body.byteStream())!!
                .copy(Bitmap.Config.ARGB_8888, true)

            val canvas = Canvas(bitmap)

            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()

            // معالجة الفقاعات مع الذكاء الاصطناعي
            if (speechBubbles.isNotEmpty()) {
                drawSpeechBubbles(
                    canvas,
                    speechBubbles,
                    imageWidth,
                    imageHeight,
                    fontSize
                )
                PerformanceMonitor.recordTiming("drawSpeechBubbles")
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
            LoggerService.info("Image processed successfully with ${speechBubbles.size} bubbles")
            return response.newBuilder()
                .body(responseBody)
                .build()
        } catch (e: Exception) {
            LoggerService.error("Error processing image", e)
            return response
        }
    }

    /**
     * رسم الفقاعات النصية مع معالجة ذكية للترجمات
     */
    private fun drawSpeechBubbles(
        canvas: Canvas,
        speechBubbles: List<Bubble>,
        imageWidth: Float,
        imageHeight: Float,
        fontSize: Int
    ) {
        var processedCount = 0
        var failedCount = 0

        speechBubbles.forEachIndexed { index, speechBubble ->
            try {
                // تصفية الفقاعات الفارغة أو غير الصحيحة
                if (!isValidBubble(speechBubble)) {
                    failedCount++
                    LoggerService.warning("Invalid bubble at index $index")
                    return@forEachIndexed
                }

                val pxX = (speechBubble.left / 100f) * imageWidth
                val pxY = (speechBubble.top / 100f) * imageHeight
                val pxWidth = (speechBubble.width / 100f) * imageWidth
                val pxHeight = (speechBubble.height / 100f) * imageHeight
                val pxCenterY = pxY + (pxHeight / 2f)

                // كشف نوع الفقاعة والاتجاه تلقائياً
                val detectedType = speechBubble.type.takeIf { it != "normal" } ?: speechBubble.detectBubbleType()
                val detectedDirection = speechBubble.direction ?: speechBubble.detectDirection()

                // معالجة ذكية للنص: تنظيف وتحسين الترجمة
                var cleanText = processTranslationText(speechBubble.text)
                
                // التحقق من الذاكرة المؤقتة
                val cacheKey = "${speechBubble.text.hashCode()}_$detectedType"
                val cachedText = TranslationCache.get(cacheKey)
                if (cachedText != null) {
                    cleanText = cachedText
                    LoggerService.info("Using cached translation for bubble $index")
                }

                if (cleanText.isEmpty()) {
                    failedCount++
                    return@forEachIndexed
                }

                // حفظ في الذاكرة المؤقتة
                TranslationCache.put(cacheKey, cleanText)

                val textPaint = createTextPaint(fontSize, speechBubble.getTextColor(), detectedType)
                val bgColor = speechBubble.getBackgroundColor()

                val bubble = createBubbleWithIntelligentSizing(
                    pxHeight,
                    pxWidth,
                    cleanText,
                    speechBubble.angle,
                    textPaint,
                    detectedType
                )

                val finalY = getYAxis(pxY, pxHeight, pxCenterY, textPaint, bubble)

                // رسم خلفية الفقاعة
                drawBubbleBackground(
                    canvas,
                    pxX,
                    finalY,
                    bubble,
                    speechBubble.angle,
                    pxWidth,
                    pxHeight,
                    bgColor
                )

                // رسم النص
                canvas.draw(textPaint, bubble, speechBubble.angle, pxX, finalY)
                
                processedCount++
                LoggerService.info("Processed bubble $index: type=$detectedType, direction=$detectedDirection")
            } catch (e: Exception) {
                failedCount++
                LoggerService.error("Error processing bubble at index $index", e, index)
            }
        }

        // تحديث الإحصائيات
        TranslationCache.updateStats(processedCount, failedCount, 0)
        LoggerService.info("Completed: $processedCount processed, $failedCount failed out of ${speechBubbles.size} total")
    }

    /**
     * التحقق من صحة الفقاعة
     */
    private fun isValidBubble(bubble: Bubble): Boolean {
        return bubble.text.isNotBlank() &&
                bubble.width > 0 &&
                bubble.height > 0 &&
                bubble.left >= 0 &&
                bubble.top >= 0 &&
                bubble.left <= 100 &&
                bubble.top <= 100
    }

    /**
     * معالجة ذكية للنص مع الترجمة
     */
    private fun processTranslationText(text: String): String {
        return try {
            // تنظيف HTML
            var cleanText = Jsoup.parse(text).text().trim()

            // إزالة المسافات الزائدة
            cleanText = cleanText.replace(Regex("\\s+"), " ").trim()

            // إزالة الترجمات المكررة
            cleanText = removeDuplicateTranslations(cleanText)

            // تطبيق تصحيحات من القاموس
            cleanText = TranslationDictionary.correct(cleanText)

            // تحسين الترجمة الضعيفة
            cleanText = improveWeakTranslations(cleanText)

            cleanText
        } catch (e: Exception) {
            LoggerService.error("Error processing text", e)
            text.trim()
        }
    }

    /**
     * إزالة الترجمات المكررة
     */
    private fun removeDuplicateTranslations(text: String): String {
        val parts = text.split(Regex("\\s*\\[|\\]\\s*"))

        return if (parts.size > 1) {
            val mainText = parts[0].trim()
            val alternativeText = if (parts.size > 1) parts[1].trim() else ""

            if (alternativeText.length > mainText.length) alternativeText else mainText
        } else {
            text
        }
    }

    /**
     * تحسين الترجمات الضعيفة
     */
    private fun improveWeakTranslations(text: String): String {
        var improved = text

        // إزالة علامات الترقيم الزائدة
        improved = improved.replace(Regex("([!?،।؛])\\1+"), "$1")

        return improved.trim()
    }

    /**
     * إنشاء فقاعة مع تحجيم ذكي
     */
    private fun createBubbleWithIntelligentSizing(
        pxHeight: Float,
        pxWidth: Float,
        text: String,
        angle: Float,
        textPaint: TextPaint,
        bubbleType: String
    ): StaticLayout {
        var optimalTextSize = textPaint.textSize
        var bubble = createBubbleLayout(pxWidth, text, textPaint)

        val maxAttempts = 20
        var attempts = 0

        // تقليل حجم الخط تدريجياً
        while (bubble.height > pxHeight && attempts < maxAttempts) {
            optimalTextSize -= 0.5f
            optimalTextSize = maxOf(optimalTextSize, MIN_FONT_SIZE)
            textPaint.textSize = optimalTextSize
            bubble = createBubbleLayout(pxWidth, text, textPaint)
            attempts++
        }

        // استعادة الإعدادات الجمالية
        textPaint.bgColor = Color.WHITE

        return bubble
    }

    private fun createBubbleLayout(pxWidth: Float, text: String, textPaint: TextPaint): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, pxWidth.toInt()).apply {
            setAlignment(Layout.Alignment.ALIGN_CENTER)
            setIncludePad(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
                setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
            }
        }.build()
    }

    /**
     * رسم خلفية الفقاعة مع تأثيرات
     */
    private fun drawBubbleBackground(
        canvas: Canvas,
        x: Float,
        y: Float,
        layout: StaticLayout,
        angle: Float,
        width: Float,
        height: Float,
        bgColor: Int
    ) {
        try {
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(angle)

            // رسم الخلفية الرئيسية
            val fillPaint = Paint().apply {
                color = bgColor
                alpha = 240
                style = Paint.Style.FILL
            }

            val padding = 10f
            canvas.drawRoundRect(
                -padding,
                -padding,
                width + padding,
                height + padding,
                8f,
                8f,
                fillPaint
            )

            // رسم ظل
            val shadowPaint = Paint().apply {
                color = Color.BLACK
                alpha = 50
                style = Paint.Style.FILL
            }

            canvas.drawRoundRect(
                -padding + 2,
                -padding + 2,
                width + padding + 2,
                height + padding + 2,
                8f,
                8f,
                shadowPaint
            )

            // رسم الحد
            val borderPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }

            canvas.drawRoundRect(
                -padding,
                -padding,
                width + padding,
                height + padding,
                8f,
                8f,
                borderPaint
            )

            canvas.restore()
        } catch (e: Exception) {
            LoggerService.warning("Error drawing bubble background: ${e.message}")
        }
    }

    private fun createTextPaint(fontSize: Int, textColor: Int, bubbleType: String): TextPaint {
        val defaultTextSize = fontSize.pt
        return TextPaint().apply {
            color = textColor
            textSize = defaultTextSize
            isAntiAlias = true
            // تكبير الخط قليلاً للصرخات
            if (bubbleType == "shout") {
                textSize = defaultTextSize * 1.1f
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
            }
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
        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL_AND_STROKE
        layout.draw(this)
        textPaint.color = foregroundColor
        textPaint.style = style
    }

    private val Int.pt: Float get() = this / SCALED_DENSITY

    companion object {
        const val SCALED_DENSITY = 0.75f
        const val MIN_FONT_SIZE = 6f
        val mediaType = "image/png".toMediaType()

        // الانتظار على ترجمات AI
        const val AI_TRANSLATION_WAIT_MS = 60000L
        const val MAX_TRANSLATION_RETRIES = 10
        const val RETRY_DELAY_MS = 5000L
    }
}
