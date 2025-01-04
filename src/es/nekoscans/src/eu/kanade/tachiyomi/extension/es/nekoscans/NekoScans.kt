package eu.kanade.tachiyomi.extension.es.nekoscans

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.util.concurrent.TimeUnit

class NekoScans : ZeistManga(
    "NekoScans",
    "https://nekoscanlationlector.blogspot.com",
    "es",
) {
    // Theme changed from MangaThemesia to ZeistManga
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val excludedCategories = listOf("Anime", "Novel")

    override val pageListSelector = "div#readarea img"
}
