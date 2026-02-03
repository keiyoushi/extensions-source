package eu.kanade.tachiyomi.extension.id.holotoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Holotoon : Madara(
    "Holotoon",
    "https://01.holotoon.site",
    "id",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH),
) {
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    override val mangaSubString = "komik"
}
