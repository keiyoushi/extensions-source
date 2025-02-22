package eu.kanade.tachiyomi.extension.en.murimscan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MurimScan : Madara("MurimScan", "https://inkreads.com", "en") {
    override val useNewChapterEndpoint = false
    override val mangaSubString = "mangax"
}
