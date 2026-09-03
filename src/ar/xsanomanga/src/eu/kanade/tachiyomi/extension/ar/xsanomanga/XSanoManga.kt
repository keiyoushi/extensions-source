package eu.kanade.tachiyomi.extension.ar.xsanomanga

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source

@Source
abstract class XSanoManga : ZeistManga() {

    // Missing popular
    override val supportsLatest = false

    override val mangaDetailsSelector = "main"

    override val mangaDetailsSelectorGenres = "dl a[rel=tag]"

    override val mangaDetailsSelectorInfo = "#extra-info dl"
    override val mangaDetailsSelectorInfoTitle = "dt"
    override val mangaDetailsSelectorInfoDescription = "dd"

    override val pageListSelector = "#reader div.separator"
}
