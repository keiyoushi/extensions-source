package eu.kanade.tachiyomi.extension.pt.ler999

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Ler999 : ZeistManga() {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()
}
