package eu.kanade.tachiyomi.extension.es.monarcamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MonarcaManga : Madara(
    "MonarcaManga",
    "https://visormonarca.com",
    "es",
    SimpleDateFormat("MMM d, yyy", Locale("es")),
) {
    override val useNewChapterEndpoint = true
}
