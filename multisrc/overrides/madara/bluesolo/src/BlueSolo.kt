package eu.kanade.tachiyomi.extension.fr.bluesolo

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class BlueSolo : Madara("Blue Solo", "https://www1.bluesolo.org", "fr", dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)) {
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Statut) + .summary-content"
}
