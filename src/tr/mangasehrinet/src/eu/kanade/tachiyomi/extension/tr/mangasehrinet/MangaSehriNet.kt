package eu.kanade.tachiyomi.extension.tr.mangasehrinet

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaSehriNet : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("tr"))
}
