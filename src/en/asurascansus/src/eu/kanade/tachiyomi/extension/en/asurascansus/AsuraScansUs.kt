package eu.kanade.tachiyomi.extension.en.asurascansus
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class AsuraScansUs : Madara("Asura Scans.us (unoriginal)", "https://asurascans.us", "en") {
    override val useNewChapterEndpoint = true
}
