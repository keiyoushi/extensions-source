package eu.kanade.tachiyomi.extension.pt.covenscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

class CovenScan :
    Madara(
        "Coven Scan",
        "https://covendasbruxonas.com",
        "pt-BR",
    ) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorAuthor = "div.post-content_item:contains(Author) > div.summary-content"
}
