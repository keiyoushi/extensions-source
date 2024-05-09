package eu.kanade.tachiyomi.extension.ja.rawxz

import eu.kanade.tachiyomi.multisrc.madara.Madara

class RawXZ : Madara(
    "RawXZ",
    "https://rawxz.net",
    "ja",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val mangaSubString = "comic"
}
