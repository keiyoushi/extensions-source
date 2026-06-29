package eu.kanade.tachiyomi.extension.ar.arbxcomix

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ArbxComix : Madara() {
    override fun searchMangaSelector() = popularMangaSelector()
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
