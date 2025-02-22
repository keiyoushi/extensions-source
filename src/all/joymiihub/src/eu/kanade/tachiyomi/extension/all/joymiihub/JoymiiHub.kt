package eu.kanade.tachiyomi.extension.all.joymiihub
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.masonry.Masonry

class JoymiiHub : Masonry("Joymii Hub", "https://www.joymiihub.com", "all")
