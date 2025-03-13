package eu.kanade.tachiyomi.extension.id.hwago

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Hwago : Madara(
    "Hwago",
    "https://hwago01.xyz",
    "id",
    dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("en")),
) {
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    override val mangaSubString = "komik"
}
