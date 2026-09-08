package eu.kanade.tachiyomi.extension.es.apollcomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ApollComics : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale("es"))
}
