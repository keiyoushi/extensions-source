package eu.kanade.tachiyomi.extension.ja.rawmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaRAW : Madara(
    "MangaRAW",
    "https://rawmanga.su",
    "ja",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true

    override val filterNonMangaItems = false

    override val mangaSubString = "r"
}
