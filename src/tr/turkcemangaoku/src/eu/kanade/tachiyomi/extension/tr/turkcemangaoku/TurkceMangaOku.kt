package eu.kanade.tachiyomi.extension.tr.turkcemangaoku

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TurkceMangaOku : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Durumu) + div.summary-content"
    override val chapterMode = ChapterMode.MangaAjax
}
