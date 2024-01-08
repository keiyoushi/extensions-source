package eu.kanade.tachiyomi.extension.es.dokkomanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class DokkoManga : Madara(
    "DokkoManga",
    "https://dokkomanga.com",
    "es",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("es")),
) {
    override val useNewChapterEndpoint = true
}
