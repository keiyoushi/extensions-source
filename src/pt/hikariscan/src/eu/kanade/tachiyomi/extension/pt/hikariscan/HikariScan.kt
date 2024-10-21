package eu.kanade.tachiyomi.extension.pt.hikariscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class HikariScan : Madara(
    "Hikari Scan",
    "https://hikariscan.org",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 2)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
