package eu.kanade.tachiyomi.extension.id.magerin

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class Magerin : ZeistManga("Magerin", "https://www.magerin.com", "id") {
    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()
}
