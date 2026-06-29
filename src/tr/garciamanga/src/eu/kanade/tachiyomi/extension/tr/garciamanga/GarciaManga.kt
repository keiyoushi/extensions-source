package eu.kanade.tachiyomi.extension.tr.garciamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class GarciaManga : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr"))
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val filterNonMangaItems = false
}
