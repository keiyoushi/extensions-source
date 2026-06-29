package eu.kanade.tachiyomi.extension.tr.asurascanstr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class AsuraScansTR : Madara() {
    override val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr"))
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val altNameSelector = ".post-content_item:contains(Diğer Adlar) .summary-content"
}
