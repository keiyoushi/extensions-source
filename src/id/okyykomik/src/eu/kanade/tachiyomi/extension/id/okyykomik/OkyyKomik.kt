package eu.kanade.tachiyomi.extension.id.okyykomik

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source

@Source
abstract class OkyyKomik : ZeistManga() {

    override val mangaDetailsSelector = "#Blog1"
    override val mangaDetailsSelectorAuthor = "#extra-info > dl:nth-child(2) dd"
    override val mangaDetailsSelectorArtist = "#extra-info > dl:nth-child(3) dd"
    override val pageListSelector = "article div.separator"

    override val supportsLatest = false
}
