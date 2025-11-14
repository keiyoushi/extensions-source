package keiyoushi.utils

import java.text.ParseException
import java.text.SimpleDateFormat

@Suppress("NOTHING_TO_INLINE")
inline fun SimpleDateFormat.tryParse(date: String?): Long {
    date ?: return 0L

    return try {
        parse(date)?.time ?: 0L
    } catch (_: ParseException) {
        0L
    }
}

/**
 * Extension function to convert string to timestamp using a SimpleDateFormat
 */
fun String?.toDate(dateFormat: SimpleDateFormat): Long {
    this ?: return 0L
    return dateFormat.tryParse(this)
}

/**
 * Extension function to convert string to URL-friendly slug
 */
fun String?.toSlug(slugSeparator: String = "-"): String {
    if (this == null) return ""

    val accentsMap = mapOf(
        'à' to 'a', 'á' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a',
        'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e',
        'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i',
        'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
        'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u',
        'ç' to 'c', 'ñ' to 'n',
    )

    return this
        .lowercase()
        .map { accentsMap[it] ?: it }
        .joinToString("")
        .replace("[^a-z0-9\\s-]".toRegex(), "")
        .replace("\\s".toRegex(), slugSeparator)
}
