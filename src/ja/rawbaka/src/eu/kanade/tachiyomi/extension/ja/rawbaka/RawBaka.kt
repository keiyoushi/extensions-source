package eu.kanade.tachiyomi.extension.ja.rawbaka

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class RawBaka : Madara() {
    override val mangaEntrySelector = ".text"
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
