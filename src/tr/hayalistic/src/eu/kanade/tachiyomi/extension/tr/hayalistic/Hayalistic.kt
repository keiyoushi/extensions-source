package eu.kanade.tachiyomi.extension.tr.hayalistic

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class Hayalistic : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
}
