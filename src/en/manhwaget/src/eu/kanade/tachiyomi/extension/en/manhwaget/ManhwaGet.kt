package eu.kanade.tachiyomi.extension.en.manhwaget

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhwaGet : Madara("ManhwaGet", "https://manhwaget.com", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
