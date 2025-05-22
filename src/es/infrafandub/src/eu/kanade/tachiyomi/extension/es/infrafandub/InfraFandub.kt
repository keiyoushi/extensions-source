package eu.kanade.tachiyomi.extension.es.infrafandub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class InfraFandub : Madara(
    "InfraFandub",
    "https://infrafandub.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale("es")),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Estado) div.summary-content"
}
