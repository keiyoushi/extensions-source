package eu.kanade.tachiyomi.extension.es.nekoscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class NekoScans : MangaThemesia(
    "NekoScans",
    "https://nekoscans.org",
    "es",
) {
    // Theme changed from ZeistManga to MangaThemesia
    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
