package eu.kanade.tachiyomi.extension.pt.hipercool

import eu.kanade.tachiyomi.multisrc.hiper.Hiper
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit

@Source
abstract class Hipercool : Hiper() {

    override fun headersBuilder() = super.headersBuilder()
        .set("x-flux-node", "G2ZsDdWhUwdU82Vw")

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
