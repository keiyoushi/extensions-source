package eu.kanade.tachiyomi.extension.es.jeazscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class JeazScans :
    Madara(
        "Jeaz Scans",
        "https://lectorhub.j5z.xyz",
        "es",
        dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("es")),
    ) {
    override val id = 5292079548510508306

    override val useNewChapterEndpoint = true

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
