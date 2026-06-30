package eu.kanade.tachiyomi.extension.es.mangaromance

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaRomance : Madara() {
    override val dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale("es"))
    override val useNewChapterEndpoint: Boolean = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
