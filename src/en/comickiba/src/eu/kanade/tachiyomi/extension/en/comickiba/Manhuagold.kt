package eu.kanade.tachiyomi.extension.en.comickiba

import eu.kanade.tachiyomi.multisrc.liliana.Liliana
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class Manhuagold : Liliana(
    "Manhuagold",
    "https://manhuagold.top",
    "en",
    usesPostSearch = true,
) {
    // MangaReader -> Liliana
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
