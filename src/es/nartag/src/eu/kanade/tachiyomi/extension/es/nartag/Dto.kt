package eu.kanade.tachiyomi.extension.es.nartag

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Serializable
class ChapterList(
    val chapters: List<Chapter>,
    val page: Int,
    val pages: Int,
)

@Serializable
class Chapter(
    val number: Float,
    val label: String,
    val relDate: String,
) {
    fun toSChapter(slug: String): SChapter = SChapter.create().apply {
        url = "/comics/$slug/cap/$number"
        name = label
        date_upload = parseDate(relDate) ?: 0L
    }
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

private val nonDigit = Regex("\\D+")

private fun parseDate(dateStr: String): Long? {
    val cleaned = dateStr.lowercase()
        .replace('í', 'i').replace('á', 'a')
        .replace('é', 'e').replace('ó', 'o')
        .replace('ú', 'u').replace('ñ', 'n')

    if (!cleaned.startsWith("hace")) return dateFormat.tryParse(dateStr)

    val num = cleaned.replace(nonDigit, "").toIntOrNull() ?: return null
    val cal = Calendar.getInstance()

    return when {
        cleaned.contains("hora") -> cal.apply { add(Calendar.HOUR, -num) }.timeInMillis
        cleaned.contains("sem") -> cal.apply { add(Calendar.DAY_OF_MONTH, -num * 7) }.timeInMillis
        cleaned.contains("dia") -> cal.apply { add(Calendar.DAY_OF_MONTH, -num) }.timeInMillis
        cleaned.contains("mes") -> cal.apply { add(Calendar.MONTH, -num) }.timeInMillis
        cleaned.contains("ano") -> cal.apply { add(Calendar.YEAR, -num) }.timeInMillis
        else -> null
    }
}
