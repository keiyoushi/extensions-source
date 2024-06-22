package eu.kanade.tachiyomi.extension.en.asurascansgg

import eu.kanade.tachiyomi.multisrc.madara.Madara

class AsuraScansGg : Madara(
    "Asura Scans.gg (unoriginal)",
    "https://asurascansgg.com",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
