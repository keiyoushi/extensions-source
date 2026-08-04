package keiyoushi.utils

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

@Deprecated(
    message = "Use Instant.tryParse, DateTimeFormatter.tryParseDate, tryParseDateTime, or tryParseZonedDateTime instead.",
)
fun SimpleDateFormat.tryParse(date: String?): Long {
    date ?: return 0L

    return parse(date, ParsePosition(0))?.time ?: 0L
}

/**
 * Parses [date] using a date only pattern, for example `yyyy-MM-dd` or `yyyy/MM/dd`, then
 * resolves it to the start of that day in [zone]. Applies when the source only sends a
 * date with no time of day.
 *
 * Any offset or zone id present in [date] is ignored, since only date fields are extracted.
 * Returns 0 if [date] is null or cannot be parsed.
 *
 * @see tryParseDateTime
 * @see tryParseZonedDateTime
 */
fun DateTimeFormatter.tryParseDate(
    date: String?,
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    date ?: return 0L

    return runCatching {
        LocalDate.parse(date, this)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}

/**
 * Parses [date] using a pattern that includes both date and time, for example
 * `yyyy-MM-dd HH:mm:ss` or `yyyy-MM-dd'T'HH:mm:ss`, then resolves it to an instant in
 * [zone]. Applies when the source sends a local date and time with no reliable offset, or
 * when an offset is present in the text but [zone] should decide the result instead.
 *
 * Any offset or zone id present in [date] is ignored, since only local date and time
 * fields are extracted; [zone] always decides the resulting instant. Returns 0 if [date]
 * is null or cannot be parsed.
 *
 * @see tryParseDate
 * @see tryParseZonedDateTime
 */
fun DateTimeFormatter.tryParseDateTime(
    date: String?,
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    date ?: return 0L

    return runCatching {
        LocalDateTime.parse(date, this)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}

/**
 * Parses [date] using a pattern whose own offset or zone id should decide the result, for
 * example `yyyy-MM-dd'T'HH:mm:ssXXX` or a pattern ending in `'['VV']'`. Applies when
 * the source's offset or zone id is trustworthy and no separate [zone] needs to be
 * supplied.
 *
 * The pattern must actually produce an offset or zone id, using X, XXX, a five letter Z,
 * or VV, or parsing fails. Returns 0 if [date] is null or cannot be parsed.
 *
 * @see tryParseDate
 * @see tryParseDateTime
 */
fun DateTimeFormatter.tryParseZonedDateTime(date: String?): Long {
    date ?: return 0L

    return runCatching {
        ZonedDateTime.parse(date, this)
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}

/**
 * Parses [date] as a full ISO 8601 instant string, for example
 * `1999-03-22T05:06:07.000Z` or `1999-03-22T05:06:07+01:00`. Applies when the source
 * already sends a self describing instant string and no [DateTimeFormatter] pattern is
 * needed.
 *
 * Returns 0 if [date] is null or cannot be parsed.
 */
fun Instant.Companion.tryParse(date: String?): Long {
    date ?: return 0L

    return parseOrNull(date)?.toEpochMilliseconds() ?: 0L
}
