package eu.kanade.tachiyomi.extension.en.novel24h

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Novel24h : Madara("24HNovel", "https://24hnovel.com", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
