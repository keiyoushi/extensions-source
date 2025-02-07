package eu.kanade.tachiyomi.extension.es.richtoscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class RichtoScan : Madara(
    "RichtoScan",
    "https://r1.richtoon.top",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ROOT),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
