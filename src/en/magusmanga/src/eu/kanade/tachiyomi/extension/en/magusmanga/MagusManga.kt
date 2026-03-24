package eu.kanade.tachiyomi.extension.en.magusmanga

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl

class MagusManga :
    Iken(
        "Magus Manga",
        "en",
        "https://magustoon.org",
        "https://api.magustoon.org",
    ) {
    // Moved from Keyoapp to Iken
    override val versionId = 3

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1)
        .build()

    override val sortPagesByFilename = true
}
