package eu.kanade.tachiyomi.extension.pt.hikariscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class HikariScan : MangaThemesia(
    "Hikari Scan",
    "https://hikariscan.org",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 2)
        .build()

    // =========================== Manga Details ============================
    override val altNamePrefix = "Títulos alternativos: "
    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(autor) i"
}
