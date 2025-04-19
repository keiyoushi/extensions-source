package eu.kanade.tachiyomi.extension.tr.mangatrnet

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaTRNet : Madara(
    "MangaTR.net",
    "https://mangatr.app",
    "tr",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
