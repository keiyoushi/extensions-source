package eu.kanade.tachiyomi.extension.es.yaoimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class YaoiManga : Madara(
    "Yaoi Manga",
    "https://yaoimanga.es",
    "es",
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
