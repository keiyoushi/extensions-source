package eu.kanade.tachiyomi.extension.es.lectormonline

import eu.kanade.tachiyomi.multisrc.lectormonline.LectorMOnline
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.util.concurrent.TimeUnit

class LectorMOnline :
    LectorMOnline(
        name = "Lector MOnline",
        baseUrl = "https://www.lectormangas.online",
        lang = "es",
    ) {
    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()
}
