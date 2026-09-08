package eu.kanade.tachiyomi.extension.es.vermanhwas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class VerManhwas : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("es"))
    override val chapterMode = ChapterMode.MangaAjax
}
