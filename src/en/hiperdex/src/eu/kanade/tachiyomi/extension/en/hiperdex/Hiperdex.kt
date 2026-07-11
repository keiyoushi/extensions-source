package eu.kanade.tachiyomi.extension.en.hiperdex

import eu.kanade.tachiyomi.multisrc.hiper.Hiper
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit

@Source
abstract class Hiperdex : Hiper() {
    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()
}
