package eu.kanade.tachiyomi.extension.en.gakamangas

import eu.kanade.tachiyomi.multisrc.madara.Madara

class GakaMangas : Madara("GakaMangas", "https://gakamangas.com", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val filterNonMangaItems = false
}
