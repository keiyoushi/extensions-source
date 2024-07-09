package eu.kanade.tachiyomi.extension.th.kumotran

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class KumoTran : Madara(
    "KumoTran",
    "https://www.kumotran.com",
    "th",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val filterNonMangaItems = false

    override val mangaDetailsSelectorTitle = ".post-title-custom"
    override val altNameSelector = ".post-content_item:contains(ชื่ออื่น) .summary-content"

    override val pageListParseSelector = ".reading-content img"
}
