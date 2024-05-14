package eu.kanade.tachiyomi.extension.en.hunlightscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class HunlightScans : Madara(
    "Hunlight Scans",
    "https://hunlight.com",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val filterNonMangaItems = false
}
