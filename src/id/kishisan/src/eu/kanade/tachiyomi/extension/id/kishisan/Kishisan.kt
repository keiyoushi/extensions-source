package eu.kanade.tachiyomi.extension.id.kishisan

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class Kishisan : ZeistManga("Kishisan", "https://www.kishisan.site", "id") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
