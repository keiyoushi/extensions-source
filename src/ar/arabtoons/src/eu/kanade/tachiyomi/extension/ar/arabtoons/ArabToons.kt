package eu.kanade.tachiyomi.extension.ar.arabtoons

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ArabToons : Madara(
    "عرب تونز",
    "https://arabtoons.net",
    "ar",
    dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale("ar")),
) {
    override val mangaDetailsSelectorStatus = "div.summary_image span.status"
    override val mangaDetailsSelectorDescription = "div.summary-text"
    override val altNameSelector = ".post-content_item:contains(أسماء أخرى) .summary-content"
}
