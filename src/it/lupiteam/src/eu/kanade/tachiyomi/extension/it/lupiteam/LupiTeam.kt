package eu.kanade.tachiyomi.extension.it.lupiteam
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.pizzareader.PizzaReader

class LupiTeam : PizzaReader("LupiTeam", "https://lupiteam.net", "it") {
    override val versionId = 2
}
