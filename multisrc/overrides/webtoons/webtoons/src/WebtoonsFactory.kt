package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.GregorianCalendar
import java.util.Locale

class WebtoonsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        WebtoonsEN(),
        WebtoonsID(),
        WebtoonsTH(),
        WebtoonsES(),
        WebtoonsFR(),
        WebtoonsZH(),
        WebtoonsDE(),
    )
}
class WebtoonsEN : WebtoonsSrc("Webtoons.com", "https://www.webtoons.com", "en")
class WebtoonsID : WebtoonsSrc("Webtoons.com", "https://www.webtoons.com", "id") {
    // Override ID as part of the name was removed to be more consiten with other enteries
    override val id: Long = 8749627068478740298

    // Android seems to be unable to parse Indonesian dates; we'll use a short hard-coded table
    // instead.
    private val dateMap: Array<String> = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des",
    )

    override fun chapterParseDate(date: String): Long {
        val expr = Regex("""(\d{4}) ([A-Z][a-z]{2}) (\d+)""").find(date) ?: return 0
        val (_, year, monthString, day) = expr.groupValues
        val monthIndex = dateMap.indexOf(monthString)
        return GregorianCalendar(year.toInt(), monthIndex, day.toInt()).time.time
    }
}
class WebtoonsTH : WebtoonsSrc("Webtoons.com", "https://www.webtoons.com", "th", dateFormat = SimpleDateFormat("d MMM yyyy", Locale("th")))
class WebtoonsES : WebtoonsSrc("Webtoons.com", "https://www.webtoons.com", "es") {
    // Android seems to be unable to parse es dates like Indonesian; we'll use a short hard-coded table instead.
    private val dateMap: Array<String> = arrayOf(
        "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic",
    )

    override fun chapterParseDate(date: String): Long {
        val expr = Regex("""(\d+)-([A-Za-z]{3})-(\d{4})""").find(date) ?: return 0
        val (_, day, monthString, year) = expr.groupValues
        val monthIndex = dateMap.indexOf(monthString.lowercase(Locale("es")))
        return GregorianCalendar(year.toInt(), monthIndex, day.toInt()).time.time
    }
}

class WebtoonsFR : WebtoonsSrc("Webtoons.com", "https://www.webtoons.com", "fr", dateFormat = SimpleDateFormat("d MMM yyyy", Locale.FRENCH)) {
    override fun String.toStatus(): Int = when {
        contains("NOUVEAU") -> SManga.ONGOING
        contains("TERMINÉ") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}

class WebtoonsZH : WebtoonsSrc("Webtoons.com", "https://www.webtoons.com", "zh-Hant", "zh-hant", "zh_TW", SimpleDateFormat("yyyy/MM/dd", Locale.TRADITIONAL_CHINESE)) {
    // Due to lang code getting more specific
    override val id: Long = 2959982438613576472
}
class WebtoonsDE : WebtoonsSrc("Webtoons.com", "https://www.webtoons.com", "de", dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN))
