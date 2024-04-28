package eu.kanade.tachiyomi.extension.es.princediciones

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class PrinceEdiciones : Madara(
    "Prince Ediciones",
    "https://princediciones.com",
    "es",
    SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val mangaSubString = "media"
    override val useNewChapterEndpoint = true
}
