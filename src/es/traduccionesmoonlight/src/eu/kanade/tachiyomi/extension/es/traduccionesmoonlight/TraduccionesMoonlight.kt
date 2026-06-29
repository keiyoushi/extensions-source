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
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimit(2) { it.host == baseUrlHost }
        .build()
}
