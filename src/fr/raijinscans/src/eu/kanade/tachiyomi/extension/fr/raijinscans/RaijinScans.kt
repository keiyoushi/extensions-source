package eu.kanade.tachiyomi.extension.fr.raijinscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class RaijinScans : Madara("Raijin Scans", "https://raijinscans.com", "fr", dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)) {
    override val useNewChapterEndpoint = true
}
