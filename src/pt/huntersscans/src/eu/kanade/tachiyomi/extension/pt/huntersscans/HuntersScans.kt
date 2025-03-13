package eu.kanade.tachiyomi.extension.pt.huntersscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class HuntersScans : Madara(
    "Hunters Scan",
    "https://hunterscomics.com",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2)
        .readTimeout(3, TimeUnit.MINUTES)
        .build()

    override val mangaSubString = "series"

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true
}
