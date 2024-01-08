package eu.kanade.tachiyomi.extension.fr.karatcamscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class KaratcamScans : Madara("Karatcam Scans", "https://karatcam-scans.fr", "fr", dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)) {
    override val mangaSubString = "projets"
}
