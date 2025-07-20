package eu.kanade.tachiyomi.extension.es.mangasx

import eu.kanade.tachiyomi.multisrc.lectormonline.LectorMOnline
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.util.concurrent.TimeUnit

class MangasX : LectorMOnline(
    name = "MangasX",
    baseUrl = "https://mangasx.online",
    lang = "es",
) {
    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()
}
