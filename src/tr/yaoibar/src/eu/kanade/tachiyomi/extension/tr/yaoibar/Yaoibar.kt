package eu.kanade.tachiyomi.extension.tr.yaoibar

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Yaoibar : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr"))
    override val useNewChapterEndpoint: Boolean = true
}
