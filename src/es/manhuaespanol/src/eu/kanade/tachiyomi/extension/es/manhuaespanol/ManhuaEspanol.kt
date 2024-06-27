package eu.kanade.tachiyomi.extension.es.manhuaespanol

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ManhuaEspanol : Madara(
    "Manhua Español",
    "https://lectorespanol.com",
    "es",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es")),
) {
    override val useNewChapterEndpoint = true
}
