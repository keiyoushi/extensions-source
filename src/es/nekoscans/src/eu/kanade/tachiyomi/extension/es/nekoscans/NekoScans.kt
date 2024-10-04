package eu.kanade.tachiyomi.extension.es.nekoscans

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Response
import java.util.concurrent.TimeUnit

class NekoScans : ZeistManga(
    "NekoScans",
    "https://www.nekoscans.org",
    "es",
) {
    // Theme changed from MangaThemesia to ZeistManga
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val excludedCategories = listOf("Anime", "Novel")

    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)
    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)
    override val supportsLatest = false

    override val pageListSelector = "div#readarea img"
}
