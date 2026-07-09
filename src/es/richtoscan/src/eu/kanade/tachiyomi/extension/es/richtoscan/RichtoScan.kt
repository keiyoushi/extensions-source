package eu.kanade.tachiyomi.extension.es.richtoscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class RichtoScan : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ROOT)
    override val client = super.client.newBuilder()
        .rateLimit(2, 1.seconds)
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)"
}
