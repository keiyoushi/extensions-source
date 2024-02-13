package eu.kanade.tachiyomi.extension.es.unitoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class Unitoon : Madara(
    "Unitoon",
    "https://lectorunitoon.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2, 1)
        .build()

    override val useNewChapterEndpoint = true
}
