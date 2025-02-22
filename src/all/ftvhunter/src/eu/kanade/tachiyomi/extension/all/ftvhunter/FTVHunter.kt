package eu.kanade.tachiyomi.extension.all.ftvhunter
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.masonry.Masonry

class FTVHunter : Masonry("FTV Hunter", "https://www.ftvhunter.com", "all")
