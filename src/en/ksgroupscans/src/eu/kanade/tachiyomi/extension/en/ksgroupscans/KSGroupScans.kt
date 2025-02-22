package eu.kanade.tachiyomi.extension.en.ksgroupscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class KSGroupScans : Madara("KSGroupScans", "https://ksgroupscans.com", "en") {
    override val versionId = 2
    override val useNewChapterEndpoint = true
}
