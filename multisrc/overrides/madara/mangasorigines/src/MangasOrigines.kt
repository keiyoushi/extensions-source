package eu.kanade.tachiyomi.extension.fr.mangasorigines

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangasOrigines : Madara("Mangas Origines", "https://mangas-origines.xyz", "fr", SimpleDateFormat("MMMM d, yyyy", Locale("fr"))) {
    override val useNewChapterEndpoint = true
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(statut) + div.summary-content"
}
