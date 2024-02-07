package eu.kanade.tachiyomi.extension.es.samuraiscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class SamuraiScan : Madara(
    "SamuraiScan",
    "https://samuraiscan.org",
    "es",
    SimpleDateFormat("d MMMM, yyyy", Locale("es")),
) {

    override val mangaSubString = "l"
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorDescription = "div.summary_content div.manga-summary"
    override val mangaDetailsSelectorStatus = "div.summary_content div.manga-authors"
}
