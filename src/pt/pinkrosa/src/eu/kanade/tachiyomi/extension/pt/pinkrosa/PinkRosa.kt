package eu.kanade.tachiyomi.extension.pt.pinkrosa

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.network.rateLimit

class PinkRosa : ZeistManga(
    "Pink Rosa",
    "https://scanpinkrosa.blogspot.com",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
