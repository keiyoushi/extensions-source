package eu.kanade.tachiyomi.extension.es.lectorasteria

import eu.kanade.tachiyomi.multisrc.moonlighttl.MoonlightTL
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl

class LectorAsteria :
    MoonlightTL(
        "Lector Asteria",
        "https://lectorasteria.com",
        "es",
    ) {

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()
}
