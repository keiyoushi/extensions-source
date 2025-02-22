package eu.kanade.tachiyomi.extension.en.s2manga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class S2Manga : Madara("S2Manga", "https://s2manga.com", "en") {

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val pageListParseSelector = "div.page-break img[src*=\"https\"]"
}
