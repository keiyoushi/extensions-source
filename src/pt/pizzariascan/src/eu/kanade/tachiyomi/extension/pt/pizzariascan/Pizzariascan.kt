package eu.kanade.tachiyomi.extension.pt.pizzariascan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Pizzariascan : Madara(
    "Pizzaria Scan",
    "https://pizzariascan.site",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt")),
) {
    override val useNewChapterEndpoint: Boolean = true
}
