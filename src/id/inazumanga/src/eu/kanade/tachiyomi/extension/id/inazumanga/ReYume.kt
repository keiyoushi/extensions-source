package eu.kanade.tachiyomi.extension.id.inazumanga

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga

class ReYume : ZeistManga("ReYume", "https://www.re-yume.my.id", "id") {

    override val popularMangaSelector = "div.PopularPosts div.grid > article"
    override val popularMangaSelectorTitle = ".post-title a"
    override val popularMangaSelectorUrl = ".item-thumbnail a"

    override val pageListSelector = ".post-body img"
}
