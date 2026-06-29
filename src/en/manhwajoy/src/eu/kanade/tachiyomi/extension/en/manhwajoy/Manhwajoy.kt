package eu.kanade.tachiyomi.extension.en.manhwajoy

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Manhwajoy : Madara("Manhwajoy", "https://manhwajoy.com", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
