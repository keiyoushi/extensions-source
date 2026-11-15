package eu.kanade.tachiyomi.extension.fr.hentaiscantrad

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HentaiScantrad : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM, yyyy", Locale.FRENCH)
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(État) + .summary-content"
    override val chapterMode = ChapterMode.MangaAjax
}
