package eu.kanade.tachiyomi.extension.es.manhwaonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class ManhwaOnline : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
