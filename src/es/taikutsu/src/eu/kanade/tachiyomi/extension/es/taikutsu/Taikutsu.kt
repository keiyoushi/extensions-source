package eu.kanade.tachiyomi.extension.es.taikutsu
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.lectormoe.LectorMoe

class Taikutsu : LectorMoe(
    "Taikutsu",
    "https://taikutsutl.capibaratraductor.com",
    "es",
)
