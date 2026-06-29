package eu.kanade.tachiyomi.extension.tr.mangasehrinet

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class MangaSehriNet : Madara() {
    override val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr"))
    override val useLoadMoreRequest = LoadMoreStrategy.AutoDetect
    override val useNewChapterEndpoint = false
}
