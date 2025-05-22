package eu.kanade.tachiyomi.extension.es.bymichiscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class BymichiScan : MangaThemesia(
    "Bymichi Scan",
    "https://bymichiby.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(baseUrl.toHttpUrl(), 3)
        .build()

    override val hasProjectPage = true
}
