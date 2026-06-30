package eu.kanade.tachiyomi.extension.es.spicyscan

import eu.kanade.tachiyomi.multisrc.spicytheme.SpicyTheme
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit

@Source
abstract class SpicyScan : SpicyTheme() {

    override val apiBaseUrl = "https://back.spicyseries.com"

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
