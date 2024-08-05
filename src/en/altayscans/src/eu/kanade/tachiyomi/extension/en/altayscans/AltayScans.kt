package eu.kanade.tachiyomi.extension.en.altayscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class AltayScans : MangaThemesia(
    "Altay Scans",
    "https://altayscans.com",
    "en",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
