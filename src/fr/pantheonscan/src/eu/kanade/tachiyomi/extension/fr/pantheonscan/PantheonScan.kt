package eu.kanade.tachiyomi.extension.fr.pantheonscan

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class PantheonScan : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)
    override val chapterMode = ChapterMode.MangaAjax
}
