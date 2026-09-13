package eu.kanade.tachiyomi.extension.all.manhwadashraw

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ManhwaDashRaw : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Status) > div.summary-content"
    override val mangaDetailsSelectorDescription = "div.post-content_item:contains(Summary) div.summary-container"
    override val pageListParseSelector = "div.page-break img.wp-manga-chapter-img"
}
