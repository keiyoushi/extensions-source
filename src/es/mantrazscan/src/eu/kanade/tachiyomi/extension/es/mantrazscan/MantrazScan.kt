package eu.kanade.tachiyomi.extension.es.mantrazscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MantrazScan : Madara(
    "Mantraz Scan",
    "https://mantrazscan.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale("es")),
) {
    override val useNewChapterEndpoint = true
}
