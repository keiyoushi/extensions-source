package eu.kanade.tachiyomi.extension.en.jinmangas

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Jinmangas : Madara("Jinmangas", "https://jinmangas.com", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
