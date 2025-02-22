package eu.kanade.tachiyomi.extension.en.platinumcrown
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class PlatinumCrown : Madara("Platinum Crown", "https://platinumscans.com", "en")
