package eu.kanade.tachiyomi.extension.en.zinmangacom

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ZinMangaCom : Madara(
    "Zin-Manga.com",
    "https://zin-manga.com",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val filterNonMangaItems = false
}
