package eu.kanade.tachiyomi.extension.tr.titanmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class TitanManga : Madara(
    "Titan Manga",
    "https://titanmanga.com",
    "tr",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
