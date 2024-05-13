package eu.kanade.tachiyomi.extension.es.tecnoscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TecnoScan : Madara(
    "Tecno Scan",
    "https://visortecno.com",
    "es",
    SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    // Site moved from MangaThemesia to Madara
    override val versionId = 4

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1, TimeUnit.SECONDS)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
