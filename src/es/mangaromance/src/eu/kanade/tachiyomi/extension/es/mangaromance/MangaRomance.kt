package eu.kanade.tachiyomi.extension.es.mangaromance

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class MangaRomance : Madara() {
    override val dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale("es"))
    override val useNewChapterEndpoint: Boolean = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
