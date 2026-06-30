package eu.kanade.tachiyomi.extension.en.vyvymangaorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class VyvyMangaOrg : Madara() {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
