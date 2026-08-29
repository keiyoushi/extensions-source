package eu.kanade.tachiyomi.extension.tr.tortugaceviri

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TortugaCeviri : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM d, yyy", Locale.forLanguageTag("tr"))
}
