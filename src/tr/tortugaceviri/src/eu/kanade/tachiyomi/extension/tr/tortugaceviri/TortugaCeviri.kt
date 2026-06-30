package eu.kanade.tachiyomi.extension.tr.tortugaceviri

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class TortugaCeviri : Madara() {
    override val dateFormat = SimpleDateFormat("MMM d, yyy", Locale("tr"))

    override val useNewChapterEndpoint = true
}
