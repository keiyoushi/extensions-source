package eu.kanade.tachiyomi.extension.tr.tempestscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class TempestScans : MangaThemesia(
    "Tempest Scans",
    "https://tempestscans.net",
    "tr",
)
