package eu.kanade.tachiyomi.extension.en.asurascansfree
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class AsuraScansFree : MangaThemesia(
    "Asura Scans Free (unoriginal)",
    "https://asurascansfree.com",
    "en",
    "/serie",
)
