package eu.kanade.tachiyomi.extension.es.nartag

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class Nartag : Madara(
    "Traducciones Amistosas",
    "https://traduccionesamistosas.eyudud.net",
    "es",
) {
    override val versionId = 2

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override val useNewChapterEndpoint = true
}
