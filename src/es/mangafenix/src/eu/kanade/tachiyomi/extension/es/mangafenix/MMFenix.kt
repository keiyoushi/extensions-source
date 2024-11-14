package eu.kanade.tachiyomi.extension.es.mangafenix

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class MMFenix : Madara(
    "MMFenix",
    "https://mmdaos.com",
    "es",
    SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {

    override val id: Long = 19158964284779393

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
