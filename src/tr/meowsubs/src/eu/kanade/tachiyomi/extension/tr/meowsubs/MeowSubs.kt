package eu.kanade.tachiyomi.extension.tr.meowsubs

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MeowSubs : Madara(
    "MeowSubs",
    "https://meowsubs.com",
    "tr",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val seriesTypeSelector = ".post-content_item:contains(Tip) .summary-content"

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
