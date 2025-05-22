package eu.kanade.tachiyomi.extension.ar.arabsdoujin

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.network.rateLimit

class ArabsDoujin : ZeistManga("Arabs Doujin", "https://www.arabsdoujin.online", "ar") {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val pageListSelector = "div.check-box"
}
