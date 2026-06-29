package eu.kanade.tachiyomi.extension.es.gremorymangas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class GremoryMangas : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))
    override val useNewChapterEndpoint = true
}
