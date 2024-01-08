package eu.kanade.tachiyomi.extension.zh.mangabz

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun parseDateInternal(source: String): Long {
    // 今天 00:00
    if (recentRegex.matches(source)) {
        val date = fullDateFormat.format(Date())
        val time = timeFormat.parse(date + ' ' + source.substring(3))!!.time
        val offset = when (source[0]) {
            '今' -> 0L
            '昨' -> 86400000L
            '前' -> 86400000L * 2
            else -> 0L // impossible
        }
        return time - offset
    }

    // 01月01号, 01月01號
    if (source.length >= 6 && source[2] == '月') {
        val year = fullDateFormat.format(Date()).substringBefore('-')
        return shortDateFormat.parse("$year $source")!!.time
    }

    // 2021-01-01
    return fullDateFormat.parse(source)!!.time
}

private val recentRegex by lazy { Regex("""[今昨前]天 \d{2}:\d{2}""") }
private val timeFormat by lazy { cstFormat("yyyy-MM-dd hh:mm") }
private val shortDateFormat by lazy { cstFormat("yyyy MM月dd") }
private val fullDateFormat by lazy { cstFormat("yyyy-MM-dd") }

private fun cstFormat(pattern: String) =
    SimpleDateFormat(pattern, Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("GMT+8") }
