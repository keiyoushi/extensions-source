package eu.kanade.tachiyomi.extension.es.traduccionesmoonlight

import eu.kanade.tachiyomi.multisrc.moonlighttl.MoonlightTL
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl

class TraduccionesMoonlight :
    MoonlightTL(
        "Traducciones Moonlight",
        "https://traduccionesmoonlight.com",
        "es",
    ) {

    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimit(2) { it.host == baseUrl.toHttpUrl().host }
        .build()
}
