package eu.kanade.tachiyomi.extension.es.mangaromance

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaRomance : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd MMMM, yyyy", Locale("es"))
    override val chapterMode = ChapterMode.MangaAjax
}
