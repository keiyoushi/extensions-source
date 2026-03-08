package eu.kanade.tachiyomi.extension.es.manhwaonline

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhwaOnline : Madara("ManhwaOnline", "https://manhwa-online.com", "es") {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
