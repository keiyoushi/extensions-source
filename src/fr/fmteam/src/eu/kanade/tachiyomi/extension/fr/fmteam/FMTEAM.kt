package eu.kanade.tachiyomi.extension.fr.fmteam
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.pizzareader.PizzaReader

class FMTEAM : PizzaReader("FMTEAM", "https://fmteam.fr", "fr") {
    override val versionId = 2
}
