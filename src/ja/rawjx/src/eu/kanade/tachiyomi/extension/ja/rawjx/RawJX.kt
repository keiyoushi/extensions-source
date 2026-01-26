package eu.kanade.tachiyomi.extension.ja.rawjx

import eu.kanade.tachiyomi.multisrc.madara.Madara

class RawJX : Madara("RawJX", "https://rawjx.com", "ja") {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
