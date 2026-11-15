package eu.kanade.tachiyomi.extension.pt.temakimangas

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source

@Source
abstract class TemakiMangas : ZeistManga() {
    override val popularMangaSelector = "#PopularPosts2 article"
    override val popularMangaSelectorTitle = "h3"
    override val popularMangaSelectorUrl = "h3 a"

    override val mangaDetailsSelector = "header"
    override val mangaDetailsSelectorThumbnail = ".thumb"
    override val mangaDetailsSelectorGenres = "dt:contains(Genre) + dd a"
    override val mangaDetailsSelectorStatus = "[data-status]"

    override val pageListSelector = "#reader div.separator"
}
