package eu.kanade.tachiyomi.extension.en.murimscan

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source

@Source
abstract class MurimScan : ZeistManga() {

    override val supportsLatest = false

    // Details
    override val mangaDetailsSelector = "main"
    override val mangaDetailsSelectorGenres = "dl.flex:contains(Genre) a[rel=tag], dl.flex:contains(Type) a[rel=tag]"
    override val mangaDetailsSelectorInfo = "dl.flex"
    override val mangaDetailsSelectorInfoTitle = "dt"
    override val mangaDetailsSelectorInfoDescription = "dd"

    // Pages
    override val pageListSelector = ".post-body, .check-box"
}
