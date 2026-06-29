package eu.kanade.tachiyomi.extension.es.colorcitoscan

import eu.kanade.tachiyomi.multisrc.spicytheme.SpicyTheme
import keiyoushi.network.rateLimit

class ColorcitoScan :
    SpicyTheme(
        name = "Colorcito Scan",
        baseUrl = "https://colorcitoscan.com",
        apiBaseUrl = "https://api.colorcitoscan.com",
        lang = "es",
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
