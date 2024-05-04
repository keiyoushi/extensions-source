package eu.kanade.tachiyomi.extension.tr.guncelmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class GuncelManga : Madara(
    "GuncelManga",
    "https://guncelmanga.net",
    "tr",
    dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr")),
) {
    override val mangaDetailsSelectorDescription = "div.description-summary"
    override val altNameSelector = ".post-content_item:contains(Diğer Adları) .summary-content"

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
