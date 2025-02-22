package eu.kanade.tachiyomi.extension.en.wuxiaworld
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class WuxiaWorld : Madara("WuxiaWorld", "https://wuxiaworld.site", "en") {
    override val mangaSubString = "novel"
    override val useNewChapterEndpoint = true
}
