package eu.kanade.tachiyomi.extension.es.topcomicporno

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class TopComicPorno : Madara() {
    override val dateFormat = SimpleDateFormat("MMM dd, yy", Locale("es"))
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
