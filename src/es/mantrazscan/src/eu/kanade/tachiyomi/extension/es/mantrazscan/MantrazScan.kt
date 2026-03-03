package eu.kanade.tachiyomi.extension.es.mantrazscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MantrazScan :
    Madara(
        "Manhwa Scan",
        "https://manhwascan.lat",
        "es",
        SimpleDateFormat("dd/MM/yyyy", Locale("es")),
    ) {
    override val id = 7172992930543738693

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
