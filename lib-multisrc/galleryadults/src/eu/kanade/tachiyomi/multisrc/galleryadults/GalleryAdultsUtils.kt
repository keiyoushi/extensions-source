package eu.kanade.tachiyomi.multisrc.galleryadults

import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar

// any space except after a comma (we're going to replace spaces only between words)
val regexSpaceNotAfterComma = Regex("""(?<!,)\s+""")

// extract preceding minus (-) and term
val regexExcludeTerm = Regex("""^(-?)"?(.+)"?""")

val regexTagCountNumber = Regex("\\([0-9,]*\\)")
val regexDateSuffix = Regex("""\d(st|nd|rd|th)""")
val regexDate = Regex("""\d\D\D""")
val regexNotNumber = Regex("""\D""")
val regexRelativeDateTime = Regex("""\d*[^0-9]*(\d+)""")

fun Element.imgAttr() = when {
    hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
    hasAttr("data-src") -> absUrl("data-src")
    hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
    hasAttr("srcset") -> absUrl("srcset").substringBefore(" ")
    else -> absUrl("src")
}

fun Element.cleanTag(): String = text().cleanTag()
fun String.cleanTag(): String = replace(regexTagCountNumber, "").trim()

// convert thumbnail URLs to full image URLs
fun String.thumbnailToFull(): String {
    val ext = substringAfterLast(".")
    return replace("t.$ext", ".$ext")
}

fun String?.toDate(simpleDateFormat: SimpleDateFormat?): Long {
    this ?: return 0L

    return if (simpleDateFormat != null) {
        if (contains(regexDateSuffix)) {
            // Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
            split(" ").map {
                if (it.contains(regexDate)) {
                    it.replace(regexNotNumber, "")
                } else {
                    it
                }
            }
                .let { simpleDateFormat.tryParse(it.joinToString(" ")) }
        } else {
            simpleDateFormat.tryParse(this)
        }
    } else {
        parseDate(this)
    }
}

private fun parseDate(date: String?): Long {
    date ?: return 0L

    return when {
        // Handle 'yesterday' and 'today', using midnight
        WordSet("yesterday", "يوم واحد").startsWith(date) -> {
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -1) // yesterday
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        WordSet("today", "just now").startsWith(date) -> {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        WordSet("يومين").startsWith(date) -> {
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -2) // day before yesterday
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        WordSet("ago", "atrás", "önce", "قبل").endsWith(date) -> {
            parseRelativeDate(date)
        }
        WordSet("hace").startsWith(date) -> {
            parseRelativeDate(date)
        }
        else -> 0L
    }
}

// Parses dates in this form: 21 hours ago OR "2 days ago (Updated 19 hours ago)"
private fun parseRelativeDate(date: String): Long {
    val number = regexRelativeDateTime.find(date)?.value?.toIntOrNull()
        ?: date.split(" ").firstOrNull()
            ?.replace("one", "1")
            ?.replace("a", "1")
            ?.toIntOrNull()
        ?: return 0L
    val now = Calendar.getInstance()

    // Sort by order
    return when {
        WordSet("detik", "segundo", "second", "วินาที").anyWordIn(date) ->
            now.apply { add(Calendar.SECOND, -number) }.timeInMillis
        WordSet("menit", "dakika", "min", "minute", "minuto", "นาที", "دقائق").anyWordIn(date) ->
            now.apply { add(Calendar.MINUTE, -number) }.timeInMillis
        WordSet("jam", "saat", "heure", "hora", "hour", "ชั่วโมง", "giờ", "ore", "ساعة", "小时").anyWordIn(date) ->
            now.apply { add(Calendar.HOUR, -number) }.timeInMillis
        WordSet("hari", "gün", "jour", "día", "dia", "day", "วัน", "ngày", "giorni", "أيام", "天").anyWordIn(date) ->
            now.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
        WordSet("week", "semana").anyWordIn(date) ->
            now.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
        WordSet("month", "mes").anyWordIn(date) ->
            now.apply { add(Calendar.MONTH, -number) }.timeInMillis
        WordSet("year", "año").anyWordIn(date) ->
            now.apply { add(Calendar.YEAR, -number) }.timeInMillis
        else -> 0L
    }
}

private fun SimpleDateFormat.tryParse(string: String): Long {
    return try {
        parse(string)?.time ?: 0L
    } catch (_: ParseException) {
        0L
    }
}

class WordSet(private vararg val words: String) {
    fun anyWordIn(dateString: String): Boolean = words.any { dateString.contains(it, ignoreCase = true) }
    fun startsWith(dateString: String): Boolean = words.any { dateString.startsWith(it, ignoreCase = true) }
    fun endsWith(dateString: String): Boolean = words.any { dateString.endsWith(it, ignoreCase = true) }
}

fun toBinary(boolean: Boolean) = if (boolean) "1" else "0"
