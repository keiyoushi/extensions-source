package eu.kanade.tachiyomi.extension.tr.anikiga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Anikiga : Madara() {
    override val dateFormat = SimpleDateFormat("d MMMMM yyyy", Locale("tr"))
}
