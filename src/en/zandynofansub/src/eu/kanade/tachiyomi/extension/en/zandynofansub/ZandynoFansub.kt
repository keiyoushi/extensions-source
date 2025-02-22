package eu.kanade.tachiyomi.extension.en.zandynofansub
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
class ZandynoFansub : Madara("Zandy no Fansub", "https://zandynofansub.aishiteru.org", "en") {
    // Migrating from FoolSlide to Madara
    override val versionId = 2
}
