package eu.kanade.tachiyomi.extension.pt.flowermangadotcom

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class FlowerMangaDotCom : Madara(
    "FlowerManga.com",
    "https://flowermangas.com",
    "pt-BR",
    SimpleDateFormat("dd MMM yyyy", Locale("pt", "BR")),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
