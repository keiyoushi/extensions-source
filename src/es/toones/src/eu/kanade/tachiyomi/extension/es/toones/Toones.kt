package eu.kanade.tachiyomi.extension.es.toones

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class Toones : Madara() {
    override val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("es"))
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
