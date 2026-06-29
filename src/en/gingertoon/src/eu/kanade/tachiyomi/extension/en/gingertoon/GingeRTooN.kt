package eu.kanade.tachiyomi.extension.en.gingertoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class GingeRTooN :
    Madara(
        "GingeRTooN",
        "https://gingertoon.com",
        "en",
        dateFormat = SimpleDateFormat("MM.dd.yyyy", Locale.ROOT),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
