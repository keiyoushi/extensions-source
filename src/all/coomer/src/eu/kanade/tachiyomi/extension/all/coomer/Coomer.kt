package eu.kanade.tachiyomi.extension.all.coomer
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.kemono.Kemono

class Coomer : Kemono("Coomer", "https://coomer.su", "all") {
    override val getTypes = listOf(
        "OnlyFans",
        "Fansly",
        "CandFans",
    )
}
