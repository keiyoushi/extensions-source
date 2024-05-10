package eu.kanade.tachiyomi.extension.en.mangaclick

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaClick : Madara(
    "MangaClick",
    "https://mangaclick.org",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
