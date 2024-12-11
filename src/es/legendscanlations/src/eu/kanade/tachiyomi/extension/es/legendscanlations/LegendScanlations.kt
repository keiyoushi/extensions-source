package eu.kanade.tachiyomi.extension.es.legendscanlations

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class LegendScanlations : Madara(
    "LegendScanlations",
    "https://legendscanlations.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale("es")),
) {
    override val useNewChapterEndpoint = true
}
