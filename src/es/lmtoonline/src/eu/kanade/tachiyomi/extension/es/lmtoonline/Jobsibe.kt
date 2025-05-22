package eu.kanade.tachiyomi.extension.es.lmtoonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
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
        .rateLimit(baseUrl.toHttpUrl(), 3)
        .build()

    override val useNewChapterEndpoint = true
}
