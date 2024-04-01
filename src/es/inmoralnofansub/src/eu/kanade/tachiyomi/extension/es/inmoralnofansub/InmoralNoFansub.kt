package eu.kanade.tachiyomi.extension.es.inmoralnofansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class InmoralNoFansub : Madara(
    "Inmoral No Fansub",
    "https://inmoralnofansub.xyz",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale("es")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
