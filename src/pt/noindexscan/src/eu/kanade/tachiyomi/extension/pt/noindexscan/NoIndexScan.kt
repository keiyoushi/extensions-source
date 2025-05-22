package eu.kanade.tachiyomi.extension.pt.noindexscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class NoIndexScan : Madara(
    "No Index Scan",
    "https://noindexscan.com",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorTitle = "div[class*='post-title'] h1"
    override val mangaDetailsSelectorStatus = "div.summary-heading:has(h5:contains(Status)) + div.summary-content"
}
