package eu.kanade.tachiyomi.extension.en.erosscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class ErosScans : MangaThemesia(
    "Eros Scans",
    "https://erosscans.xyz",
    "en",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
