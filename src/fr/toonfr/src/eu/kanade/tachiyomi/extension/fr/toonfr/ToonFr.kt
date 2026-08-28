package eu.kanade.tachiyomi.extension.fr.toonfr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ToonFr : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM d", Locale("fr"))
    override val chapterMode = ChapterMode.MangaAjax

    override val mangaSubString = "webtoon"

    override val mangaDetailsSelectorTitle = ".post-content h3"
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Statut) + div.summary-content"
    override val altNameSelector = ".post-content_item:contains(Autre nom) .summary-content"
}
