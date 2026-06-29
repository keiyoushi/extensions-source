package eu.kanade.tachiyomi.extension.es.topcomicpornonet

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class TopComicPornoNet : Madara() {
    override val dateFormat = SimpleDateFormat("MMM dd, yy", Locale("es"))
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
