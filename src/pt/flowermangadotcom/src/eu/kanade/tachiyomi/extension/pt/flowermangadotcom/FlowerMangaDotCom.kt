package eu.kanade.tachiyomi.extension.pt.flowermangadotcom

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class FlowerMangaDotCom : Madara(
    "FlowerManga.com",
    "https://flowermanga.com",
    "pt-BR",
    SimpleDateFormat("dd MMMMM yyyy", Locale("pt", "BR")),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
