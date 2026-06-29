package eu.kanade.tachiyomi.extension.tr.mangawow

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class MangaWOW : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr"))
}
