package eu.kanade.tachiyomi.extension.en.gakamangas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class GakaMangas : Madara() {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val filterNonMangaItems = false
}
