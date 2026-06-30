package eu.kanade.tachiyomi.extension.en.gingertoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class GingeRTooN : Madara() {
    override val dateFormat = SimpleDateFormat("MM.dd.yyyy", Locale.ROOT)
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
