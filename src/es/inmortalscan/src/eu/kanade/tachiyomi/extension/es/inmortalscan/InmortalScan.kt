package eu.kanade.tachiyomi.extension.es.inmortalscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class InmortalScan : Madara() {
    override val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("es"))
    override val mangaSubString = "mg"

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true
}
