package eu.kanade.tachiyomi.extension.tr.mangatrnet

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaTRNet : Madara(
    "MangaTR.net",
    "https://mangatr.net",
    "tr",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
