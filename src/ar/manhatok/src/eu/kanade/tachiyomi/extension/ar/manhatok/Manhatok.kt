package eu.kanade.tachiyomi.extension.ar.manhatok

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit

@Source
abstract class Manhatok : ZeistManga() {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
