package eu.kanade.tachiyomi.extension.en.boratscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class BoratScans : Madara(
    "Borat Scans",
    "https://boratscans.com",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
