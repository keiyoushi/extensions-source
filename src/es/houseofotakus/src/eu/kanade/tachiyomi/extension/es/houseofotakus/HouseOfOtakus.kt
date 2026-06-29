package eu.kanade.tachiyomi.extension.es.houseofotakus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class HouseOfOtakus : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
