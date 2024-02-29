package eu.kanade.tachiyomi.extension.es.tresdaosscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TresDaosScan : Madara(
    "Tres Daos Scan",
    "https://tresdaos.com",
    "es",
    SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    // Site move from MangaThemesia to Madara
    override val versionId = 2

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint = true

    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()
}
