package eu.kanade.tachiyomi.extension.es.luckymanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class LuckyManga : Madara(
    "Lucky Manga",
    "https://luckymanga.com",
    "es",
    SimpleDateFormat("d MMMM, yyyy", Locale("es")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
