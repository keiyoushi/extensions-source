package eu.kanade.tachiyomi.extension.ar.mangalink

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Mangalink : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.forLanguageTag("ar"))
    override val chapterMode = ChapterMode.MangaPage
}
