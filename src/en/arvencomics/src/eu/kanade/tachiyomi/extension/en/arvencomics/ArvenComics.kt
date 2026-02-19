package eu.kanade.tachiyomi.extension.en.arvencomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class ArvenComics :
    Madara(
        "Arven Scans",
        "https://arvencomics.com",
        "en",
    ) {
    // migrated from Keyoapp to Madara
    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimit(1)
        .build()

    override val mangaSubString = "comic"
    override val useNewChapterEndpoint = true
}
