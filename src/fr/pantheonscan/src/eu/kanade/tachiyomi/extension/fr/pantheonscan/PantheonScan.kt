package eu.kanade.tachiyomi.extension.fr.pantheonscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class PantheonScan : Madara("Pantheon Scan", "https://pantheon-scan.com", "fr", dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE)) {
    override val useNewChapterEndpoint = true
}
