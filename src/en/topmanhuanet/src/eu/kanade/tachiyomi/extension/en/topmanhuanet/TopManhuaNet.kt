package eu.kanade.tachiyomi.extension.en.topmanhuanet

import eu.kanade.tachiyomi.multisrc.madara.Madara

class TopManhuaNet : Madara(
    "TopManhua.net",
    "https://topmanhua.net",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
