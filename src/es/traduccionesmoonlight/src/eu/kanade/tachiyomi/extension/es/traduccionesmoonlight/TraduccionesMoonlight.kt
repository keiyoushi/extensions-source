package eu.kanade.tachiyomi.extension.es.traduccionesmoonlight

import eu.kanade.tachiyomi.multisrc.moonlighttl.MoonlightTL
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl

class TraduccionesMoonlight :
    MoonlightTL(
        "Traducciones Moonlight",
        "https://traduccionesmoonlight.com",
        "es",
    ) {

    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()
}
