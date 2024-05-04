package eu.kanade.tachiyomi.extension.en.freecomiconline

import eu.kanade.tachiyomi.multisrc.madara.Madara

class FreeComicOnline : Madara(
    "Free Comic Online",
    "https://freecomiconline.me",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val mangaSubString = "comic"
}
