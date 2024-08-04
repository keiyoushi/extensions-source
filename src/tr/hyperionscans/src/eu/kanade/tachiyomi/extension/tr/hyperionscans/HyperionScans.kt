package eu.kanade.tachiyomi.extension.tr.hyperionscans

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class HyperionScans : ZeistManga(
    "Hyperion Scans",
    "https://www.hyperionscans.site",
    "tr",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val popularMangaSelector = "div#PopularPosts3 article"
    override val popularMangaSelectorTitle = "h3"
    override val popularMangaSelectorUrl = "a"
}
