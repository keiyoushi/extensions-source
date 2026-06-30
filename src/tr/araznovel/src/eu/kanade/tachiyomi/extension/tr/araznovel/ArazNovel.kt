package eu.kanade.tachiyomi.extension.tr.araznovel

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class ArazNovel : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
}
