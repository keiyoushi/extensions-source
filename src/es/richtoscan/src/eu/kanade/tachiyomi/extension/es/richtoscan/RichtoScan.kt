package eu.kanade.tachiyomi.extension.es.richtoscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class RichtoScan : Madara(
    "RichtoScan",
    "https://r1.richtoon.top",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ROOT),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
