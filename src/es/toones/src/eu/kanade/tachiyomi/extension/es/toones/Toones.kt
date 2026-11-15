package eu.kanade.tachiyomi.extension.es.toones

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Toones : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale("es"))
}
