package eu.kanade.tachiyomi.extension.id.nimemob

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class Nimemob : ZeistManga("Nimemob", "https://www.nimemob.my.id", "id") {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val popularMangaSelector = "div#PopularPosts2 div.grid > article"
    override val popularMangaSelectorTitle = "h3 a"
    override val popularMangaSelectorUrl = popularMangaSelectorTitle

    override val mangaDetailsSelector = "main"
    override val mangaDetailsSelectorGenres = "dl dt:contains(Genre) + dd a"

    override val pageListSelector = "article#reader div.separator"
}
