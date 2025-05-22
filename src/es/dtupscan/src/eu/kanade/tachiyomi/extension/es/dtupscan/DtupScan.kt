package eu.kanade.tachiyomi.extension.es.dtupscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class DtupScan : MangaThemesia(
    "De Todo Un Poco Scan",
    "https://dtupscan.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(baseUrl.toHttpUrl(), 3)
        .build()
}
