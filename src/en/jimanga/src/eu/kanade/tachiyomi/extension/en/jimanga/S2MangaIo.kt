package eu.kanade.tachiyomi.extension.en.jimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class S2MangaIo : Madara("S2Manga.io", "https://s2manga.io", "en") {
    override val id = 4144734547277607826

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
    override val filterNonMangaItems = false
}
