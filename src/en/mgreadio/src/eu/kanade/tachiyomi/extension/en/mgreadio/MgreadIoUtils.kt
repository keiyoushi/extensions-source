package eu.kanade.tachiyomi.extension.en.mgreadio

import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// SimpleDateFormat 'X' requires API 24, so we use 'Z' and normalize "+07:00" -> "+0700".
private val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
private val restChapterDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("GMT+7")
}
private val isoTimeZoneRegex = Regex("""([+-]\d{2}):(\d{2})$""")

internal fun Element.imageUrl(): String? = when (normalName()) {
    "meta" -> attr("content")
    else -> attr("abs:data-src").ifBlank {
        attr("abs:data-lazy-src").ifBlank {
            attr("abs:src")
        }
    }
}.takeIf(String::isNotBlank)

internal fun String?.parseStatus(): Int = when (this?.lowercase(Locale.US)?.trim()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "season end", "source hiatus", "caught up" -> SManga.ON_HIATUS
    "dropped" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

internal fun String?.parseChapterDate(): Long {
    if (this.isNullOrBlank()) return 0L

    val normalized = isoTimeZoneRegex.replace(this, "$1$2")
        .replace(Regex("""Z$"""), "+0000")

    return try {
        chapterDateFormat.parse(normalized)!!.time
    } catch (_: ParseException) {
        0L
    }
}

internal fun String?.parseRestChapterDate(): Long {
    if (this.isNullOrBlank()) return 0L

    return try {
        restChapterDateFormat.parse(this)!!.time
    } catch (_: ParseException) {
        0L
    }
}

internal fun Float.toChapterNamePart(): String = if (this % 1f == 0f) {
    toInt().toString()
} else {
    toString()
}
