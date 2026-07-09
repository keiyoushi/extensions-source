package eu.kanade.tachiyomi.extension.tr.hayalistic

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Hayalistic : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
}
