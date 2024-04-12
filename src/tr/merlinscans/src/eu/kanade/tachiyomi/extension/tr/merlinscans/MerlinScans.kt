package eu.kanade.tachiyomi.extension.tr.merlinscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MerlinScans : Madara("Merlin Scans", "https://merlinscans.com", "tr", SimpleDateFormat("MMMM dd, yyyy", Locale("tr"))) {

    override val useNewChapterEndpoint = true
}
