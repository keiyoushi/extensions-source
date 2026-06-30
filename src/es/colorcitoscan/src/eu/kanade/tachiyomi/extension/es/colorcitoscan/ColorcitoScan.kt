package eu.kanade.tachiyomi.extension.es.colorcitoscan

import eu.kanade.tachiyomi.multisrc.spicytheme.SpicyTheme
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit

@Source
abstract class ColorcitoScan : SpicyTheme() {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
