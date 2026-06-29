package eu.kanade.tachiyomi.extension.es.mundomanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class MundoManhwa : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es"))
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
