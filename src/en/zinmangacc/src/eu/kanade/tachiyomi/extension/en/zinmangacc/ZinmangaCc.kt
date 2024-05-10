package eu.kanade.tachiyomi.extension.en.zinmangacc

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ZinmangaCc : Madara(
    "Zinmanga.cc",
    "https://zinmanga.cc",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
