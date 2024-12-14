package eu.kanade.tachiyomi.extension.en.astrascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class AstraScans : MangaThemesia(
    "Astra Scans",
    "https://astrascans.org",
    "en",
    "/series",
) {
    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()
}
