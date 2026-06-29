package eu.kanade.tachiyomi.extension.tr.tortugaceviri

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class TortugaCeviri : Madara() {
    override val dateFormat = SimpleDateFormat("MMM d, yyy", Locale("tr"))

    override val useNewChapterEndpoint = true
}
