package eu.kanade.tachiyomi.extension.en.bakkin
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.bakkin.BakkinReaderX

class Bakkin : BakkinReaderX("Bakkin", "https://bakkin.moe/reader/", "en")
