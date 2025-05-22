package eu.kanade.tachiyomi.extension.id.magerin

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.network.rateLimit

class Magerin : ZeistManga("Magerin", "https://www.magerin.com", "id") {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
