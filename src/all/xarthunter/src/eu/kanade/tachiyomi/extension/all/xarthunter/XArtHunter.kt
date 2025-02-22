package eu.kanade.tachiyomi.extension.all.xarthunter
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.masonry.Masonry

class XArtHunter : Masonry("XArt Hunter", "https://www.xarthunter.com", "all")
