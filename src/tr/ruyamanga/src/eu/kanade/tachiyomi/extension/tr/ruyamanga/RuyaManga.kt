package eu.kanade.tachiyomi.extension.tr.ruyamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class RuyaManga : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
    override val filterNonMangaItems = false
}
