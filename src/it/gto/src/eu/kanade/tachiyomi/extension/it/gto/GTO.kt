package eu.kanade.tachiyomi.extension.it.gto
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.pizzareader.PizzaReader

class GTO : PizzaReader("GTO The Great Site", "https://reader.gtothegreatsite.net", "it") {
    override val versionId = 2
}
