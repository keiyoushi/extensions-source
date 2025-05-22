package eu.kanade.tachiyomi.extension.ar.manhatok

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.network.rateLimit

class Manhatok : ZeistManga("Manhatok", "https://manhatok.blogspot.com", "ar") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
