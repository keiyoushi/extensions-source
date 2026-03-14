package eu.kanade.tachiyomi.extension.en.mangafree

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Mangafree : Madara("Mangafree", "https://mangafree.info", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
