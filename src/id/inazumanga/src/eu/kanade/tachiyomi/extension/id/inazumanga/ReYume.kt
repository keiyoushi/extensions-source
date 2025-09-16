package eu.kanade.tachiyomi.extension.id.inazumanga

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga

class ReYume : ZeistManga("ReYume", "https://www.re-yume.my.id", "id") {

    override val popularMangaSelector = "div.PopularPosts div.grid > article"
    override val popularMangaSelectorTitle = ".post-title a"
    override val popularMangaSelectorUrl = ".item-thumbnail a"

    override val mangaDetailsSelector = "#main"
    override val mangaDetailsSelectorGenres = "a[rel=tag]"
    override val mangaDetailsSelectorAuthor = "#extra-info dl:contains(Author) dd"
    override val mangaDetailsSelectorArtist = "#extra-info dl:contains(Artist) dd"
    override val mangaDetailsSelectorAltName = "#extra-info dl:contains(Alternative) dd"
    override val mangaDetailsSelectorInfo = "div:has(h1)"
    override val mangaDetailsSelectorInfoTitle = "h1"
    override val mangaDetailsSelectorInfoDescription = "span"

    override val pageListSelector = ".post-body img"
}
