package eu.kanade.tachiyomi.extension.zh.mangabz

import keiyoushi.utils.tryParseDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun parseDateInternal(source: String): Long {
    // 今天 00:00
    if (recentRegex.matches(source)) {
        val time = LocalTime.parse(source.substring(3), timeFormat)
        val offset = when (source[0]) {
            '今' -> 0L
            '昨' -> 1L
            '前' -> 2L
            else -> 0L // impossible
        }
        return LocalDate.now(cstZone).minusDays(offset).atTime(time)
            .atZone(cstZone).toInstant().toEpochMilli()
    }

    // 01月01号, 01月01號
    if (source.length >= 6 && source[2] == '月') {
        val year = LocalDate.now(cstZone).year
        return shortDateFormat.tryParseDate("$year ${source.take(5)}", cstZone)
    }

    // 2021-01-01
    return fullDateFormat.tryParseDate(source, cstZone)
}

private val recentRegex = Regex("""[今昨前]天 \d{2}:\d{2}""")
private val cstZone = ZoneId.of("GMT+8")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val shortDateFormat = DateTimeFormatter.ofPattern("yyyy MM月dd", Locale.ENGLISH)
private val fullDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
