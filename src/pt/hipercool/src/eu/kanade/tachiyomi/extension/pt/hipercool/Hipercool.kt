package eu.kanade.tachiyomi.extension.pt.hipercool

import eu.kanade.tachiyomi.multisrc.hiper.Hiper
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit

@Source
abstract class Hipercool : Hiper() {
    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()
}
