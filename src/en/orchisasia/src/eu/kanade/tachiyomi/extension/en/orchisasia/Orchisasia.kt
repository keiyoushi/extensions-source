package eu.kanade.tachiyomi.extension.en.orchisasia

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class Orchisasia : Madara() {
    override val mangaSubString = "comic"
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
