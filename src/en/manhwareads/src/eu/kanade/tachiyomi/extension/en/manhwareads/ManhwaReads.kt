package eu.kanade.tachiyomi.extension.en.manhwareads

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhwaReads : Madara("Manhwa Reads", "https://manhwareads.com", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
