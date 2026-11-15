package eu.kanade.tachiyomi.extension.fr.toonfr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

@Source
abstract class ToonFr : Madara() {
    override val chapterMode = ChapterMode.MangaAjax

    override val mangaSubString = "webtoon"

    override val mangaDetailsSelectorTitle = ".post-content h3"
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Statut) + div.summary-content"
    override val altNameSelector = ".post-content_item:contains(Autre nom) .summary-content"

    override fun parseChapterDate(date: String?): Long {
        val value = date?.trim() ?: return 0L
        val normalized = value.lowercase(Locale.ROOT)
        val today = LocalDate.now(TIME_ZONE)

        if (arrayOf("today", "aujourd'hui").any(normalized::startsWith)) {
            return today.atStartOfDay(TIME_ZONE).toInstant().toEpochMilli()
        }
        if (arrayOf("yesterday", "hier").any(normalized::startsWith)) {
            return today.minusDays(1).atStartOfDay(TIME_ZONE).toInstant().toEpochMilli()
        }

        val relativeMatch = RELATIVE_REGEX.find(normalized)
        if (relativeMatch != null) {
            val amount = relativeMatch.groupValues[1].toLongOrNull()
            val unitStr = relativeMatch.groupValues[2]
            val unit = when {
                unitStr.startsWith("sec") -> ChronoUnit.SECONDS
                unitStr.startsWith("min") -> ChronoUnit.MINUTES
                unitStr.startsWith("heure") || unitStr.startsWith("hour") -> ChronoUnit.HOURS
                unitStr.startsWith("jour") || unitStr.startsWith("day") -> ChronoUnit.DAYS
                unitStr.startsWith("semaine") || unitStr.startsWith("week") -> ChronoUnit.WEEKS
                unitStr.startsWith("mois") || unitStr.startsWith("month") -> ChronoUnit.MONTHS
                unitStr.startsWith("an") || unitStr.startsWith("year") -> ChronoUnit.YEARS
                else -> null
            }
            if (amount != null && unit != null) {
                return ZonedDateTime.now(TIME_ZONE).minus(amount, unit).toInstant().toEpochMilli()
            }
        }

        val superDate = super.parseChapterDate(date)
        if (superDate != 0L) {
            return superDate
        }

        val m1 = MONTH_FIRST_REGEX.matchEntire(value)
        if (m1 != null) {
            val (monthStr, dayStr, yearStr) = m1.destructured
            val month = MONTHS[monthStr.lowercase(Locale.ROOT).trimEnd('.')]
            val day = dayStr.toIntOrNull()
            if (month != null && day != null) {
                return toEpochMilli(day, month, yearStr.takeIf(String::isNotBlank)?.toIntOrNull(), today)
            }
        }

        val m2 = DAY_FIRST_REGEX.matchEntire(value)
        if (m2 != null) {
            val (dayStr, monthStr, yearStr) = m2.destructured
            val month = MONTHS[monthStr.lowercase(Locale.ROOT).trimEnd('.')]
            val day = dayStr.toIntOrNull()
            if (month != null && day != null) {
                return toEpochMilli(day, month, yearStr.takeIf(String::isNotBlank)?.toIntOrNull(), today)
            }
        }

        val m3 = NUMERIC_DATE_REGEX.matchEntire(value)
        if (m3 != null) {
            val (dayStr, monthStr, yearStr) = m3.destructured
            val day = dayStr.toIntOrNull()
            val month = monthStr.toIntOrNull()
            var year = yearStr.toIntOrNull()
            if (day != null && month != null && year != null) {
                if (year < 100) year += 2000
                return runCatching {
                    LocalDate.of(year, month, day).atStartOfDay(TIME_ZONE).toInstant().toEpochMilli()
                }.getOrDefault(0L)
            }
        }

        return 0L
    }

    private fun toEpochMilli(day: Int, month: Int, year: Int?, today: LocalDate): Long = runCatching {
        val parsedYear = year ?: today.year
        var chapterDate = LocalDate.of(parsedYear, month, day)
        if (year == null && chapterDate.isAfter(today)) {
            chapterDate = chapterDate.minusYears(1)
        }
        chapterDate.atStartOfDay(TIME_ZONE).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    companion object {
        private val TIME_ZONE: ZoneId = ZoneId.of("Europe/Paris")

        private val RELATIVE_REGEX = Regex("""(?:il y a\s+)?(\d+)\s*([a-zA-ZÀ-ÿ.]+)(?:\s+ago)?""", RegexOption.IGNORE_CASE)
        private val MONTH_FIRST_REGEX = Regex("""^([a-zA-ZÀ-ÿ.]+)\s+(\d{1,2})(?:,?\s+(\d{4}))?$""")
        private val DAY_FIRST_REGEX = Regex("""^(\d{1,2})\s+([a-zA-ZÀ-ÿ.]+)(?:\s+(\d{4}))?$""")
        private val NUMERIC_DATE_REGEX = Regex("""^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})$""")

        private val MONTHS = mapOf(
            // French short & full
            "janv" to 1, "jan" to 1, "janvier" to 1,
            "fev" to 2, "fevr" to 2, "fév" to 2, "févr" to 2, "fevrier" to 2, "février" to 2,
            "mar" to 3, "mars" to 3,
            "avr" to 4, "avril" to 4,
            "mai" to 5,
            "juin" to 6,
            "juil" to 7, "juillet" to 7,
            "aout" to 8, "août" to 8,
            "sep" to 9, "sept" to 9, "septembre" to 9,
            "oct" to 10, "octobre" to 10,
            "nov" to 11, "novembre" to 11,
            "dec" to 12, "déc" to 12, "decembre" to 12, "décembre" to 12,
            // English short & full
            "feb" to 2, "february" to 2,
            "apr" to 4, "april" to 4,
            "may" to 5,
            "jun" to 6, "june" to 6,
            "jul" to 7, "july" to 7,
            "aug" to 8, "august" to 8,
            "september" to 9,
            "october" to 10,
            "november" to 11,
            "december" to 12,
        )
    }
}
