package eu.kanade.tachiyomi.extension.fr.shadowtrad

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Shadowtrad : Madara("Shadowtrad", "https://shadowtrad.net", "fr", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.FRANCE)) {
    override val useNewChapterEndpoint = true
    override val mangaDetailsSelectorAuthor = "div.manga-authors > a"
    override val mangaDetailsSelectorDescription = "div.manga-summary > .description, div.manga-summary"
    override val chapterUrlSuffix = ""
}
