package eu.kanade.tachiyomi.extension.en.s2manga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class S2Manga : Madara() {

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val pageListParseSelector = "div.page-break img[src*=\"https\"]"
}
