package eu.kanade.tachiyomi.extension.es.spicyscan

import eu.kanade.tachiyomi.multisrc.spicytheme.SpicyTheme
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class SpicyScan :
    SpicyTheme(
        name = "Spicy Scan",
        baseUrl = "https://spicyseries.com",
        apiBaseUrl = "https://back.spicyseries.com",
        lang = "es",
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
