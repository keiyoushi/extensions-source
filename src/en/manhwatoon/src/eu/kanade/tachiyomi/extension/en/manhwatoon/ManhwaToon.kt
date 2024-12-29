package eu.kanade.tachiyomi.extension.en.manhwatoon

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhwaToon : Madara(
    "Manhwa Toon",
    "https://www.manhwatoon.com",
    "en",
) {
    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
