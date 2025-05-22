package eu.kanade.tachiyomi.extension.pt.flowermanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class FlowerMangaDotNet : Madara(
    "FlowerManga.net",
    "https://flowermanga.net",
    "pt-BR",
    SimpleDateFormat("d 'de' MMMMM 'de' yyyy", Locale("pt", "BR")),
) {

    override val id = 2421010180391442293

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
