package eu.kanade.tachiyomi.extension.pt.noindexscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class NoIndexScan : Madara(
    "No Index Scan",
    "https://noindexscan.com",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorTitle = "div[class*='post-title'] h1"
    override val mangaDetailsSelectorStatus = "div.summary-heading:has(h5:contains(Status)) + div.summary-content"
}
