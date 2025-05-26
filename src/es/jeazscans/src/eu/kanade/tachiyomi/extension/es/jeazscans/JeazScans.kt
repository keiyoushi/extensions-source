package eu.kanade.tachiyomi.extension.es.jeazscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class JeazScans : MangaThemesia(
    "Jeaz Scans",
    "https://lectorhub.j5z.xyz",
    "es",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale("es")),
) {
    override val id = 5292079548510508306

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
