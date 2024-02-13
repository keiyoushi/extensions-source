package eu.kanade.tachiyomi.extension.es.unitoonoficial

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class UnitoonOficial : Madara(
    "Unitoon Oficial",
    "https://unitoonoficial.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale("es")),
) {
    override val useNewChapterEndpoint = true
}
