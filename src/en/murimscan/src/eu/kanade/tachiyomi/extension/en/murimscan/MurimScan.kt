package eu.kanade.tachiyomi.extension.en.murimscan

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source

@Source
abstract class MurimScan : ZeistManga() {

    override val supportsLatest = false

    override val mangaDetailsSelector = "main"
    override val mangaDetailsSelectorDescription = ".entry-content"
    override val mangaDetailsSelectorGenres = "h1.entry-title + div > span:first-child, h1.entry-title ~ div a[rel=tag]"
    override val mangaDetailsSelectorStatus = "h1.entry-title + div > span:nth-child(3)"

    override val pageListSelector = ".chapter-raw-content:not(.chapter-raw-content ~ .chapter-raw-content)"
}
