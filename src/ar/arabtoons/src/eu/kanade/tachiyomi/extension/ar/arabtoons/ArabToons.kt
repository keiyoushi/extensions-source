package eu.kanade.tachiyomi.extension.ar.arabtoons

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ArabToons : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.forLanguageTag("ar"))
    override val mangaDetailsSelectorStatus = "div.summary_image span.status"
    override val mangaDetailsSelectorDescription = "div.summary-text"
    override val altNameSelector = ".post-content_item:contains(أسماء أخرى) .summary-content"
}
