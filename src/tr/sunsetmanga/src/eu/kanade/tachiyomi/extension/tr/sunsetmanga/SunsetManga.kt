package eu.kanade.tachiyomi.extension.tr.sunsetmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class SunsetManga : Madara() {
    override val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
