package eu.kanade.tachiyomi.extension.en.manhuaplus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ManhuaPlus : Madara() {

    // The website does not flag the content.
    override val filterNonMangaItems = false
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val pageListParseSelector = ".read-container img"
}
