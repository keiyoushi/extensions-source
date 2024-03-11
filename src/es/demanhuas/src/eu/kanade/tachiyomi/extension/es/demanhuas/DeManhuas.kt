package eu.kanade.tachiyomi.extension.es.demanhuas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class DeManhuas : Madara(
    "DeManhuas",
    "https://demanhuas.com",
    "es",
    SimpleDateFormat("MMMM d, yyyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override val mangaSubString = "sm"
    override val useNewChapterEndpoint = true
}
