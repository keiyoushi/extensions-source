package eu.kanade.tachiyomi.extension.pt.covenscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class CovenScan : Madara(
    "Coven Scan",
    "https://cvnscan.com",
    "pt-BR",
    SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorAuthor = "div.post-content_item:contains(Author) > div.summary-content"
}
