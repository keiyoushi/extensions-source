package eu.kanade.tachiyomi.extension.en.manga1k

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Manga1k : Madara(
    "Manga1k",
    "https://manga1k.com",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
