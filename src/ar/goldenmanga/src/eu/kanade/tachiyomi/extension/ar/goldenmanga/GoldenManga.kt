package eu.kanade.tachiyomi.extension.ar.goldenmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class GoldenManga : Madara("Golden Manga", "https://goldenmanga.net", "ar") {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
