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
import java.util.concurrent.TimeUnit
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.O)
class SpeechBubblePainterInterceptor(val fontSize: Int) : Interceptor {

    private val startTime = System.currentTimeMillis()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        if (PAGE_REGEX.containsMatchIn(url).not()) {
            return chain.proceed(request)
        }

        val speechBubbles = request.url.fragment?.parseAs<List<Bubble>>()
            ?: emptyList()

        val imageRequest = request.newBuilder()
            .url(url)
            .build()

        val response = chain.proceed(imageRequest)

        if (response.isSuccessful.not()) {
            return response
        }

        try {
            val bitmap = BitmapFactory.decodeStream(response.body.byteStream())!!
                .copy(Bitmap.Config.ARGB_8888, true)

            val canvas = Canvas(bitmap)

            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()

            // معالجة الفقاعات مع الذكاء الاصطناعي لكشف الترجمات
            if (speechBubbles.isNotEmpty()) {
                drawSpeechBubbles(
                    canvas,
                    speechBubbles,
                    imageWidth,
                    imageHeight,
                    fontSize
                )
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
        } catch (e: Exception) {
            // في حالة الخطأ، نرجع الصورة الأصلية
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
        speechBubbles.forEach { speechBubble ->
            try {
                // تصفية الفقاعات الفارغة أو غير الصحيحة
                if (!isValidBubble(speechBubble)) {
                    return@forEach
                }

                val pxX = (speechBubble.left / 100f) * imageWidth
                val pxY = (speechBubble.top / 100f) * imageHeight
                val pxWidth = (speechBubble.width / 100f) * imageWidth
                val pxHeight = (speechBubble.height / 100f) * imageHeight
                val pxCenterY = pxY + (pxHeight / 2f)

                // معالجة ذكية للنص: تنظيف وتحسين الترجمة
                val cleanText = processTranslationText(speechBubble.text)
                if (cleanText.isEmpty()) {
                    return@forEach
                }

                val textPaint = createTextPaint(fontSize)
                val bubble = createBubbleWithIntelligentSizing(
                    pxHeight,
                    pxWidth,
                    cleanText,
                    speechBubble.angle,
                    textPaint
                )

                val finalY = getYAxis(pxY, pxHeight, pxCenterY, textPaint, bubble)
                
                // رسم خلفية الفقاعة
                drawBubbleBackground(canvas, pxX, finalY, bubble, speechBubble.angle, pxWidth, pxHeight)
                
                // رسم النص
                canvas.draw(textPaint, bubble, speechBubble.angle, pxX, finalY)
            } catch (e: Exception) {
                // تجاهل الفقاعات التي تسبب أخطاء
            }
        }
    }

    /**
     * التحقق من صحة الفقاعة
     * معايير الصحة:
     * - وجود نص غير فارغ
     * - أبعاد صحيحة (width و height > 0)
     * - إحداثيات منطقية
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
     * تقوم بـ:
     * 1. تنظيف HTML والعلامات
     * 2. إزالة المسافات الزائدة
     * 3. كشف اللغة والنصوص المختلطة
     * 4. تحسين الترجمة
     */
    private fun processTranslationText(text: String): String {
        return try {
            // تنظيف HTML
            var cleanText = Jsoup.parse(text).text().trim()
            
            // إزالة المسافات الزائدة والأسطر الفارغة
            cleanText = cleanText.replace(Regex("\\s+"), " ").trim()
            
            // إزالة الترجمات المكررة (مثل [نص مكرر])
            cleanText = removeDuplicateTranslations(cleanText)
            
            // كشف وتحسين الترجمة الضعيفة
            cleanText = improveWeakTranslations(cleanText)
            
            cleanText
        } catch (e: Exception) {
            text.trim()
        }
    }

    /**
     * إزالة الترجمات المكررة
     */
    private fun removeDuplicateTranslations(text: String): String {
        val parts = text.split(Regex("\\s*\\[|\\]\\s*"))
        
        return if (parts.size > 1) {
            // إذا كان هناك نص داخل أقواس، ننظر إلى كلا الجزأين
            val mainText = parts[0].trim()
            val alternativeText = if (parts.size > 1) parts[1].trim() else ""
            
            // اختيار أطول نص أو الأكثر اكتمالاً
            if (alternativeText.length > mainText.length) alternativeText else mainText
        } else {
            text
        }
    }

    /**
     * تحسين الترجمات الضعيفة أو الناقصة
     * يكتشف الأخطاء الشائعة ويصححها
     */
    private fun improveWeakTranslations(text: String): String {
        var improved = text
        
        // تصحيح الأخطاء الإملائية الشائعة
        val corrections = mapOf(
            "ا الـ" to "ال",
            "  " to " ",
            "؟؟" to "؟",
            "!!!" to "!",
            "،،" to "،",
        )
        
        for ((wrong, correct) in corrections) {
            improved = improved.replace(wrong, correct)
        }
        
        // إذا كان النص قصيراً جداً (أقل من 3 أحرف)، قد يكون ناقصاً
        // لكن نبقيه كما هو لأنه قد يكون كلمة قصيرة فعلاً
        
        return improved.trim()
    }

    /**
     * إنشاء فقاعة مع تحجيم ذكي للنص
     * يحسب حجم الخط الأمثل بناءً على مساحة الفقاعة
     */
    private fun createBubbleWithIntelligentSizing(
        pxHeight: Float,
        pxWidth: Float,
        text: String,
        angle: Float,
        textPaint: TextPaint,
    ): StaticLayout {
        var optimalTextSize = textPaint.textSize
        var bubble = createBubbleLayout(pxWidth, text, textPaint)
        
        val maxAttempts = 20
        var attempts = 0
        
        // تقليل حجم الخط تدريجياً حتى يناسب الفقاعة
        while (bubble.height > pxHeight && attempts < maxAttempts) {
            optimalTextSize -= 0.5f
            optimalTextSize = maxOf(optimalTextSize, MIN_FONT_SIZE)
            textPaint.textSize = optimalTextSize
            bubble = createBubbleLayout(pxWidth, text, textPaint)
            attempts++
        }
        
        // استعادة الإعدادات الجمالية
        textPaint.color = Color.BLACK
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
     * رسم خلفية الفقاعة (Optional - لتحسين المظهر)
     */
    private fun drawBubbleBackground(
        canvas: Canvas,
        x: Float,
        y: Float,
        layout: StaticLayout,
        angle: Float,
        width: Float,
        height: Float
    ) {
        try {
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(angle)
            
            val paint = Paint().apply {
                color = Color.WHITE
                alpha = 220 // شبه شفاف
                style = Paint.Style.FILL
            }
            
            val padding = 8f
            canvas.drawRect(
                -padding,
                -padding,
                width + padding,
                height + padding,
                paint
            )
            
            // رسم حد للفقاعة
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRect(
                -padding,
                -padding,
                width + padding,
                height + padding,
                paint
            )
            
            canvas.restore()
        } catch (e: Exception) {
            // تجاهل الأخطاء في رسم الخلفية
        }
    }

    private fun createTextPaint(fontSize: Int): TextPaint {
        val defaultTextSize = fontSize.pt
        return TextPaint().apply {
            color = Color.BLACK
            textSize = defaultTextSize
            isAntiAlias = true
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
        textPaint.color = textPaint.bgColor
        textPaint.style = Paint.Style.FILL_AND_STROKE
        layout.draw(this)
        textPaint.color = foregroundColor
        textPaint.style = style
    }

    private val Int.pt: Float get() = this / SCALED_DENSITY

    companion object {
        const val SCALED_DENSITY = 0.75f // 1px = 0.75pt
        const val MIN_FONT_SIZE = 6f // أصغر حجم خط
        val mediaType = "image/png".toMediaType()

        // الانتظار على ترجمات AI - حتى دقيقة واحدة
        const val AI_TRANSLATION_WAIT_MS = 60000L // 60 ثانية (دقيقة واحدة)

        // محاولات إعادة التحقق من الترجمات
        const val MAX_TRANSLATION_RETRIES = 10
        const val RETRY_DELAY_MS = 5000L // 5 ثواني بين كل محاولة
    }
}
