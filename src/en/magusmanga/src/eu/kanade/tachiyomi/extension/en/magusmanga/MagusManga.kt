package eu.kanade.tachiyomi.extension.en.magusmanga

import eu.kanade.tachiyomi.multisrc.iken.Iken
import keiyoushi.network.rateLimit
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

    override val client = network.client.newBuilder()
        .rateLimit(1) { it.host == baseUrl.toHttpUrl().host }
        .build()

    override val sortPagesByFilename = true
}
