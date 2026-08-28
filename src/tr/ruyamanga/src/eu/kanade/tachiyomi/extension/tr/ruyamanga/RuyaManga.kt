package eu.kanade.tachiyomi.extension.tr.ruyamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class RuyaManga : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
    override val filterNonMangaItems = false
}
