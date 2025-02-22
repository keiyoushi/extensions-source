package eu.kanade.tachiyomi.extension.it.phoenixscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.pizzareader.PizzaReader

class PhoenixScans : PizzaReader("Phoenix Scans", "https://www.phoenixscans.com", "it") {
    override val versionId = 2
}
