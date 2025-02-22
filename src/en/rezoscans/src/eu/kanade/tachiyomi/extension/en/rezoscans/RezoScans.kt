package eu.kanade.tachiyomi.extension.en.rezoscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class RezoScans : Keyoapp(
    "Rezo Scans",
    "https://rezoscans.com",
    "en",
)
