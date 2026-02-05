package eu.kanade.tachiyomi.extension.pt.plumacomics

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class PlumaComics :
    MangaThemesia(
        "Pluma Comics",
        "https://plumacomics.cloud",
        "pt-BR",
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    // Moved from YuYu to Madara
    override val versionId = 4
}
