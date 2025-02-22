package eu.kanade.tachiyomi.extension.en.manhwaclan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhwaClan : Madara("ManhwaClan", "https://manhwaclan.com", "en")
