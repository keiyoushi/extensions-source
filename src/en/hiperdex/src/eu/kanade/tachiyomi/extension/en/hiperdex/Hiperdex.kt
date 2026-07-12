package eu.kanade.tachiyomi.extension.en.hiperdex

import eu.kanade.tachiyomi.multisrc.hiper.Hiper
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit

@Source
abstract class Hiperdex : Hiper() {

    override fun headersBuilder() = super.headersBuilder()
        .set("x-hpx-nexus", "hpx-block-f91")

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
