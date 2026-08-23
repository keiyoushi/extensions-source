package eu.kanade.tachiyomi.extension.es.topcomicpornonet

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TopComicPornoNet : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM dd, yy", Locale("es"))
    override val chapterMode = ChapterMode.MangaAjax
}
