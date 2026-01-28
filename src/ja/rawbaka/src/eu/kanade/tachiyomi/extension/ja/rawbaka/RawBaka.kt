package eu.kanade.tachiyomi.extension.ja.rawbaka

import eu.kanade.tachiyomi.multisrc.madara.Madara

class RawBaka : Madara("RawBaka", "https://rawbaka.com", "ja") {
    override val mangaEntrySelector = ".text"
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
