package eu.kanade.tachiyomi.extension.pt.flowermanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class FlowerMangaDotNet :
    Madara(
        "FlowerManga.net",
        "https://flowermangas.net",
        "pt-BR",
        SimpleDateFormat("d 'de' MMMMM 'de' yyyy", Locale("pt", "BR")),
    ) {

    override val id = 2421010180391442293

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
