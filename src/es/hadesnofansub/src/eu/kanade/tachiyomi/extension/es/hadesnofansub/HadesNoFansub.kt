package eu.kanade.tachiyomi.extension.es.hadesnofansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HadesNoFansub : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale("es"))
    override val chapterMode = ChapterMode.MangaAjax

    override val mangaSubString = "tmo"

    override val mangaDetailsSelectorStatus = "div.summary_content > div.post-content div.post-content_item:has(div.summary-heading:contains(Status)) div.summary-content"

    override val mangaDetailsSelectorTag = "div.tags-content a.notUsed" // Site uses this for the scanlator
}
