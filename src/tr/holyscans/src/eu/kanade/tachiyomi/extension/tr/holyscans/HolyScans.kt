package eu.kanade.tachiyomi.extension.tr.holyscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class HolyScans : Madara("Holy Scans", "https://holyscans.com.tr", "tr") {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
