package eu.kanade.tachiyomi.extension.ar.mangalek

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.utils.tryParseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Mangalek : Madara() {

    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.forLanguageTag("ar"))
    private val formatTwo = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    override fun parseChapterDate(date: String?) = chapterDateFormat.tryParseDate(date).takeIf {
        it != 0L
    } ?: formatTwo.tryParseDate(date)
}
