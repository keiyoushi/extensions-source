package eu.kanade.tachiyomi.extension.en.manhuaplusorg
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.liliana.Liliana

class ManhuaPlusOrg : Liliana(
    "ManhuaPlus (Unoriginal)",
    "https://manhuaplus.org",
    "en",
)
