package eu.kanade.tachiyomi.extension.es.inventariooculto

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class InventarioOculto : Madara() {
    override val dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale("es"))
    override val useNewChapterEndpoint = true
}
