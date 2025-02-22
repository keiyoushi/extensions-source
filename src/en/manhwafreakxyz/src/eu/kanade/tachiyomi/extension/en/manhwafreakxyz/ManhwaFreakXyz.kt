package eu.kanade.tachiyomi.extension.en.manhwafreakxyz
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class ManhwaFreakXyz : MangaThemesia(
    "ManhwaFreak.xyz",
    "https://manhwafreak.xyz",
    "en",
) {
    override val seriesStatusSelector = ".status-value"
}
