package eu.kanade.tachiyomi.extension.ar.mangahub

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class MangaHub : ZeistManga(
    "MangaHub",
    "https://www.mangahub.link",
    "ar",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val popularMangaSelector = "div#PopularPosts2 article"
    override val popularMangaSelectorTitle = "h3"
    override val popularMangaSelectorUrl = "a"

    override val mangaDetailsSelector = ".grid.gap-5.gta-series"
    override val mangaDetailsSelectorGenres = "dt:contains(التصنيف) + dd a[rel=tag]"

    override val pageListSelector = "article#reader .separator, div.image-container"
}
