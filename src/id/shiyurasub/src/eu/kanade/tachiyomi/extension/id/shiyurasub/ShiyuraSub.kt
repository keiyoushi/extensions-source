package eu.kanade.tachiyomi.extension.id.shiyurasub

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source

@Source
abstract class ShiyuraSub : ZeistManga() {

    override val hasFilters = true
    override val hasLanguageFilter = false

    override val supportsLatest = false

    override val mangaDetailsSelectorDescription = "#synopsis ~ p"
}
