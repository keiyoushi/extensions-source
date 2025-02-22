package eu.kanade.tachiyomi.extension.es.darknebulus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class DarkNebulus : Madara(
    "Dark Nebulus",
    "https://www.darknebulus.com",
    "es",
    dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("es")),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
