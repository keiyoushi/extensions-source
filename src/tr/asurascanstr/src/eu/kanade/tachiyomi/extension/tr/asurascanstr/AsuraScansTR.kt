package eu.kanade.tachiyomi.extension.tr.asurascanstr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class AsuraScansTR : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("tr"))
    override val altNameSelector = ".post-content_item:contains(Diğer Adlar) .summary-content"
}
