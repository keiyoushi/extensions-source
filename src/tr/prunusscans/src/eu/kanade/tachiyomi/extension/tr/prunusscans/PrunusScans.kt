package eu.kanade.tachiyomi.extension.tr.prunusscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class PrunusScans : MangaThemesia(
    "Prunus Scans",
    "https://prunusscans.com",
    "tr",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
