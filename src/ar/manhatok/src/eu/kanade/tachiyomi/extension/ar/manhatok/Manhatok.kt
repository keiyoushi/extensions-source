package eu.kanade.tachiyomi.extension.ar.manhatok

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.util.concurrent.TimeUnit

class Manhatok : ZeistManga("Manhatok", "https://manhatok.blogspot.com", "ar") {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
