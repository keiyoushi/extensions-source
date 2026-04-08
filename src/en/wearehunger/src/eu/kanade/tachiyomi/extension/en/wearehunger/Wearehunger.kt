package eu.kanade.tachiyomi.extension.en.wearehunger

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Wearehunger :
    Madara(
        "Wearehunger",
        "https://www.wearehunger.site",
        "en",
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
