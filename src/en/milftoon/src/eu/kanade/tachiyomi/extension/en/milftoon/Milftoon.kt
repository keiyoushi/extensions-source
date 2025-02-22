package eu.kanade.tachiyomi.extension.en.milftoon
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Milftoon : Madara("Milftoon", "https://milftoon.xxx", "en") {
    override val mangaSubString = "comics"
}
