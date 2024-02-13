package eu.kanade.tachiyomi.extension.es.emperorscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class EmperorScan : Madara(
    "Emperor Scan",
    "https://emperorscan.com",
    "es",
    SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val mangaDetailsSelectorDescription = "div.sinopsis div.contenedor"
}
