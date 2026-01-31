package eu.kanade.tachiyomi.extension.en.ero18x

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Ero18x : Madara("Ero18x", "https://ero18x.com", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
