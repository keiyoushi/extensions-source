package eu.kanade.tachiyomi.extension.es.manhuaonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class ManhuaOnline : Madara(
    "Manhua Online",
    "https://manhuaonline.org",
    "es",
    SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorTitle = "div.summary-content span.rate-title"

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
