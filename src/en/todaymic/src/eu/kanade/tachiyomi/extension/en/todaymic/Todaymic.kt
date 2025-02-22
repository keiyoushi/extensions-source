package eu.kanade.tachiyomi.extension.en.todaymic
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Todaymic : Madara("Todaymic", "https://todaymic.com", "en") {
    override val filterNonMangaItems = false
    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val mangaDetailsSelectorDescription = ".manga-about > p:nth-child(2)"
}
