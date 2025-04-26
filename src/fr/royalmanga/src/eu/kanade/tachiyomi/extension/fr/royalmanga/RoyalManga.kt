package eu.kanade.tachiyomi.extension.fr.royalmanga

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class RoyalManga : ZeistManga("Royal Manga", "https://www.royalmanga.com", "fr") {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val popularMangaSelector = "div#PopularPosts2 div.grid > article"
    override val popularMangaSelectorTitle = "h3 a"
    override val popularMangaSelectorUrl = popularMangaSelectorTitle

    override val mangaDetailsSelector = "main"
    override val mangaDetailsSelectorGenres = "dl dt:contains(Genre) + dd a"
    override val mangaDetailsSelectorAuthor = "u > b:contains(Auteur) + dd"

    override val pageListSelector = "article#reader div.separator"
}
