package eu.kanade.tachiyomi.extension.en.blazescans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class Blazescans : MangaThemesia("Blazescans", "https://blazescans.com", "en") {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2)
        .build()
}
