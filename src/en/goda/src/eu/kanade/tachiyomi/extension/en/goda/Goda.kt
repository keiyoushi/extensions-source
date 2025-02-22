package eu.kanade.tachiyomi.extension.en.goda
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.goda.GoDa

class Goda : GoDa("Goda", "https://manhuascans.org", "en")
