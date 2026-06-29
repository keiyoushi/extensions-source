package eu.kanade.tachiyomi.extension.es.stickhorse

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class StickHorse : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es"))
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
