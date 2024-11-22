package eu.kanade.tachiyomi.extension.es.manhwases

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwasEs : Madara(
    "Manhwas.es",
    "https://manhwas.es",
    "es",
    dateFormat = SimpleDateFormat("MMM dd, yy", Locale("es")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2)
        .build()

    override val useNewChapterEndpoint = true
}
