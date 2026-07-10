package eu.kanade.tachiyomi.extension.ar.mangatek

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class WrappedSerializer<T>(val dataSerializer: KSerializer<T>) : KSerializer<Wrapped<T>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Wrapped")

    override fun deserialize(decoder: Decoder): Wrapped<T> {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Expected Json Decoder")
        val array = input.decodeJsonElement().jsonArray

        // array[0] is the index, array[1] is the content
        val index = array[0].jsonPrimitive.int
        val value = input.json.decodeFromJsonElement(dataSerializer, array[1])

        return Wrapped(index, value)
    }

    override fun serialize(encoder: Encoder, value: Wrapped<T>) = throw SerializationException("Serialization is not supported")
}

@Serializable(with = WrappedSerializer::class)
class Wrapped<T>(
    val index: Int,
    val value: T,
)

@Serializable
class MangaWrapper(
    val manga: Wrapped<MangaData>,
)

@Serializable
class MangaData(
    @SerialName("MangaChapters")
    val mangaChapters: Wrapped<List<Wrapped<ChapterItem>>>,
)

@Serializable
class ChapterItem(
    @SerialName("chapter_number") val chapterNumber: Wrapped<String>,
    val title: Wrapped<String?>,
    @SerialName("created_at") val createdAt: Wrapped<String?>,
)

@Serializable
class PageDTO(
    val imageUrl: String,
    val bubbles: List<Bubble> = emptyList(),
) {
    fun hasSpeechBubbles() = bubbles.isNotEmpty()
}

/**
 * بيانات الفقاعة النصية المحسّنة
 * مع دعم أنواع مختلفة من الفقاعات والذكاء الاصطناعي
 */
@Serializable
class Bubble(
    val text: String = "",
    val left: Float = 0.0f,
    val top: Float = 0.0f,
    val width: Float = 0.0f,
    val height: Float = 0.0f,
    val angle: Float = 0.0f,
    // ألوان مخصصة (اختيارية)
    val bgColor: String? = null,  // لون الخلفية (hex: #RRGGBB)
    val textColor: String? = null, // لون النص (hex: #RRGGBB)
    // نوع الفقاعة
    val type: String = "normal", // normal, shout, whisper, thought
    // اتجاه النص
    val direction: String? = null, // rtl أو ltr
) {
    /**
     * كشف نوع الفقاعة من النص
     * - Shout: نص بأحرف كبيرة أو بعلامات تعجب
     * - Whisper: نص صغير أو بين قوسين
     * - Thought: نص عادي
     */
    fun detectBubbleType(): String {
        return when {
            text.count { it.isUpperCase() } > text.length * 0.6 -> "shout"
            text.contains("!!!") || text.contains("!") && text.length < 10 -> "shout"
            text.startsWith("(") && text.endsWith(")") -> "whisper"
            text.startsWith("...") -> "thought"
            else -> "normal"
        }
    }

    /**
     * كشف اتجاه النص (RTL للعربية، LTR للإنجليزية)
     */
    fun detectDirection(): String {
        return when {
            text.any { it.code in 0x0600..0x06FF } -> "rtl" // عربي
            text.any { it.code in 0x0590..0x05FF } -> "rtl" // عبري
            else -> "ltr"
        }
    }

    /**
     * الحصول على لون الخلفية المناسب
     */
    fun getBackgroundColor(): Int {
        return when {
            bgColor != null -> hexToColor(bgColor)
            type == "shout" -> 0xFFFFCC00.toInt() // أصفر للصرخات
            type == "whisper" -> 0xFFE0E0E0.toInt() // رمادي فاتح للهمس
            type == "thought" -> 0xFFFFC0CB.toInt() // وردي للأفكار
            else -> 0xFFFFFFFF.toInt() // أبيض للعادي
        }
    }

    /**
     * الحصول على لون النص المناسب
     */
    fun getTextColor(): Int {
        return when {
            textColor != null -> hexToColor(textColor)
            type == "shout" -> 0xFF000000.toInt() // أسود للصرخات
            type == "whisper" -> 0xFF666666.toInt() // رمادي غامق للهمس
            else -> 0xFF000000.toInt() // أسود عادي
        }
    }

    private fun hexToColor(hex: String): Int {
        return try {
            val color = hex.removePrefix("#").toLong(16)
            (color or 0xFF000000L).toInt()
        } catch (e: Exception) {
            0xFFFFFFFF.toInt()
        }
    }
}

/**
 * بيانات إحصائيات الترجمات
 */
@Serializable
data class TranslationStats(
    val totalBubbles: Int = 0,
    val processedBubbles: Int = 0,
    val failedBubbles: Int = 0,
    val averageProcessingTime: Long = 0,
    val cacheHits: Int = 0,
    val cacheMisses: Int = 0,
) {
    val cacheHitRate: Float
        get() = if (cacheHits + cacheMisses > 0) cacheHits.toFloat() / (cacheHits + cacheMisses) else 0f
    
    val successRate: Float
        get() = if (totalBubbles > 0) processedBubbles.toFloat() / totalBubbles else 0f
}

/**
 * سجل الأخطاء والعمليات (للتتبع والمراقبة)
 */
@Serializable
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO", // INFO, WARNING, ERROR
    val message: String = "",
    val bubbleIndex: Int? = null,
    val exception: String? = null,
)

/**
 * قاموس محلي لتحسين الترجمات
 */
object TranslationDictionary {
    private val corrections = mapOf(
        // أخطاء إملائية شائعة
        "ا الـ" to "ال",
        "الـ " to "ال ",
        "  " to " ",
        "؟؟" to "؟",
        "!!!" to "!",
        "،،" to "،",
        // اختصارات شائعة
        "ك ل" to "كل",
        "ف ي" to "في",
        "ع ن" to "عن",
    )

    private val arabicStopWords = setOf(
        "ال", "و", "أو", "من", "في", "ب", "ل", "ك", "عن", "على", "إلى", "هذا", "ذلك"
    )

    fun correct(text: String): String {
        var corrected = text
        for ((wrong, correct) in corrections) {
            corrected = corrected.replace(wrong, correct, ignoreCase = false)
        }
        return corrected.trim()
    }

    fun isStopWord(word: String): Boolean = arabicStopWords.contains(word)

    fun removeStopWords(text: String): String {
        return text.split(" ")
            .filterNot { isStopWord(it) }
            .joinToString(" ")
    }
}

/**
 * مدير التخزين المؤقت للترجمات
 */
object TranslationCache {
    private val cache = mutableMapOf<String, String>()
    private var maxCacheSize = 1000
    private var stats = TranslationStats()

    fun get(key: String): String? {
        val value = cache[key]
        if (value != null) {
            stats = stats.copy(cacheHits = stats.cacheHits + 1)
        } else {
            stats = stats.copy(cacheMisses = stats.cacheMisses + 1)
        }
        return value
    }

    fun put(key: String, value: String) {
        if (cache.size >= maxCacheSize) {
            // حذف أقدم 10% من العناصر
            val itemsToRemove = cache.size / 10
            cache.entries.take(itemsToRemove).forEach { cache.remove(it.key) }
        }
        cache[key] = value
    }

    fun clear() {
        cache.clear()
        stats = TranslationStats()
    }

    fun getStats(): TranslationStats = stats

    fun updateStats(processed: Int, failed: Int, time: Long) {
        stats = stats.copy(
            processedBubbles = stats.processedBubbles + processed,
            failedBubbles = stats.failedBubbles + failed,
            averageProcessingTime = (stats.averageProcessingTime + time) / 2
        )
    }
}

/**
 * نظام التسجيل (Logging System)
 */
object LoggerService {
    private val logs = mutableListOf<LogEntry>()
    private var maxLogSize = 500
    private var enableLogging = true

    fun info(message: String, bubbleIndex: Int? = null) {
        if (enableLogging) {
            addLog(LogEntry(level = "INFO", message = message, bubbleIndex = bubbleIndex))
        }
    }

    fun warning(message: String, bubbleIndex: Int? = null) {
        if (enableLogging) {
            addLog(LogEntry(level = "WARNING", message = message, bubbleIndex = bubbleIndex))
        }
    }

    fun error(message: String, exception: Exception? = null, bubbleIndex: Int? = null) {
        if (enableLogging) {
            addLog(
                LogEntry(
                    level = "ERROR",
                    message = message,
                    exception = exception?.message,
                    bubbleIndex = bubbleIndex
                )
            )
        }
    }

    private fun addLog(entry: LogEntry) {
        if (logs.size >= maxLogSize) {
            logs.removeAt(0)
        }
        logs.add(entry)
    }

    fun getLogs(): List<LogEntry> = logs.toList()

    fun clearLogs() = logs.clear()

    fun setLogging(enabled: Boolean) {
        enableLogging = enabled
    }
}

/**
 * نظام تحسين الأداء والقياسات
 */
object PerformanceMonitor {
    private val timings = mutableMapOf<String, MutableList<Long>>()
    private var startTime = 0L

    fun startTimer() {
        startTime = System.currentTimeMillis()
    }

    fun recordTiming(operationName: String) {
        if (startTime == 0L) return
        val duration = System.currentTimeMillis() - startTime
        timings.getOrPut(operationName) { mutableListOf() }.add(duration)
        startTime = 0L
    }

    fun getAverageTime(operationName: String): Long {
        val times = timings[operationName] ?: return 0
        return if (times.isNotEmpty()) times.average().toLong() else 0
    }

    fun getReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Performance Report ===")
        timings.forEach { (operation, times) ->
            sb.appendLine("$operation: avg=${times.average().toLong()}ms, total=${times.sum()}ms, count=${times.size}")
        }
        return sb.toString()
    }

    fun clear() = timings.clear()
}
