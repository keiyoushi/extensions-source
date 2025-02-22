package eu.kanade.tachiyomi.extension.ca.fansubscathentai
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.fansubscat.FansubsCat

class HentaiCat : FansubsCat(
    "Hentai.cat",
    "https://manga.hentai.cat",
    "ca",
    "https://api.hentai.cat",
    isHentaiSite = true,
) {
    override val id: Long = 7575385310756416449
}
