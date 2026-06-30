package eu.kanade.tachiyomi.extension.es.stickhorse

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class StickHorse : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es"))
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
