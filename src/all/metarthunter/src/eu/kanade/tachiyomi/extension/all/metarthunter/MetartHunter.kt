package eu.kanade.tachiyomi.extension.all.metarthunter
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.masonry.Masonry

class MetartHunter : Masonry("Metart Hunter", "https://www.metarthunter.com", "all")
