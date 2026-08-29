package eu.kanade.tachiyomi.extension.ar.arabmanhwa

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ArabManhwa : MadaraNoAjax() {
    override val chapterMode = ChapterMode.MangaAjax
    override fun archiveSelector() = "article.page-item-detail"
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.forLanguageTag("ar"))
}
