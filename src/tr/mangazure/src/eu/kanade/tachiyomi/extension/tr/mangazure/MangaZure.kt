package eu.kanade.tachiyomi.extension.tr.mangazure

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaZure : Madara() {
    override val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("tr"))
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
