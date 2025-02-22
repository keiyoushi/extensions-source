package eu.kanade.tachiyomi.extension.en.wickedscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class WickedScans : Keyoapp("Wicked Scans", "https://wickedscans.org", "en")
