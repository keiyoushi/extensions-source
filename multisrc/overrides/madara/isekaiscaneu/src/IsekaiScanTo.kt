package eu.kanade.tachiyomi.extension.en.isekaiscaneu

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class IsekaiScanTo : Madara("IsekaiScan.to (unoriginal)", "https://m.isekaiscan.to", "en", SimpleDateFormat("MM/dd/yyyy", Locale.US)) {
    override val id = 8608305834807261892L; // from former IsekaiScan.eu source

    override val mangaSubString = "mangax"
}
