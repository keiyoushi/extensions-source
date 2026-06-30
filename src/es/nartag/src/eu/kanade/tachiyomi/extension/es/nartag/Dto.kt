package eu.kanade.tachiyomi.extension.es.nartag

import keiyoushi.utils.tryParse
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

private val nonDigit = Regex("\\D+")

fun parseDate(dateStr: String): Long? {
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
