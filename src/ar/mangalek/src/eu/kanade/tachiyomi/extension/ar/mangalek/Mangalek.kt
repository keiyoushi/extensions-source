package eu.kanade.tachiyomi.extension.ar.mangalek

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.utils.tryParseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Mangalek : Madara() {

    override val filterNonMangaItems get() = false

    override val chapterMode = ChapterMode.AdminAjax

    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("ar"))
    private val formatTwo = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    override fun parseChapterDate(date: String?) = super.parseChapterDate(date).takeIf {
        it != 0L
    } ?: formatTwo.tryParseDate(date)
}
