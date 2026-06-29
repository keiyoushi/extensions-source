package eu.kanade.tachiyomi.extension.th.catzaa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class Catzaa : Madara() {
    override val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("th"))
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
