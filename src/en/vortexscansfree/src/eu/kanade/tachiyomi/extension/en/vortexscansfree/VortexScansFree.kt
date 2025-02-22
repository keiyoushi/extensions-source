package eu.kanade.tachiyomi.extension.en.vortexscansfree
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class VortexScansFree : MangaThemesia(
    "Vortex Scans Free (unoriginal)",
    "https://vortexscansfree.com",
    "en",
)
