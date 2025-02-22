package eu.kanade.tachiyomi.extension.ca.fansubscat
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.fansubscat.FansubsCat

class FansubsCat : FansubsCat(
    "Fansubs.cat",
    "https://manga.fansubs.cat",
    "ca",
    "https://api.fansubs.cat",
    isHentaiSite = false,
)
