package eu.kanade.tachiyomi.extension.es.zevep

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Zevep : Madara(
    "Zevep",
    "https://zevep.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val useNewChapterEndpoint: Boolean = true
}
