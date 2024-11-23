package eu.kanade.tachiyomi.extension.es.lmtoonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class Jobsibe : Madara(
    "Jobsibe",
    "https://lmtos.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es")),
) {

    override val id = 3752522006902890093

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1)
        .build()

    override val useNewChapterEndpoint = true
}
