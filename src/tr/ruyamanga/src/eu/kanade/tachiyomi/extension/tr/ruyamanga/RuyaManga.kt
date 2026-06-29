package eu.kanade.tachiyomi.extension.tr.ruyamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class RuyaManga : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
    override val filterNonMangaItems = false
}
