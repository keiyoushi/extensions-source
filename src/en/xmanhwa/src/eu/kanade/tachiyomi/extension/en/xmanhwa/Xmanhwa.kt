package eu.kanade.tachiyomi.extension.en.xmanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Xmanhwa : Madara(
    "Xmanhwa",
    "https://www.xmanhwa.me",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val filterNonMangaItems = false
}
