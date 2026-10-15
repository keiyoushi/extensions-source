package eu.kanade.tachiyomi.extension.pt.arthurscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ArthurScan : Madara() {
    override val supportsPostId = false
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("pt-BR"))
    override val chapterMode = ChapterMode.MangaAjax

    override val filterGenresSelector = ".genre-item"
}
