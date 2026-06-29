package eu.kanade.tachiyomi.extension.es.inventariooculto

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class InventarioOculto : Madara() {
    override val dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale("es"))
    override val useNewChapterEndpoint = true
}
