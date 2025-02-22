package eu.kanade.tachiyomi.extension.en.factmanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class FactManga : Madara("FactManga", "https://factmanga.com", "en") {
    override val useNewChapterEndpoint = true
}
