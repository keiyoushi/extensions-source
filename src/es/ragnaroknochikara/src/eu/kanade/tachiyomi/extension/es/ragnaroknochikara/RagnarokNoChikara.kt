package eu.kanade.tachiyomi.extension.es.ragnaroknochikara

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class RagnarokNoChikara : Madara(
    "Ragnarok No Chikara",
    "https://ragnarokscan.com",
    "es",
    SimpleDateFormat("MMMM d, yyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
