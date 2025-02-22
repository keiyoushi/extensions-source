package eu.kanade.tachiyomi.extension.en.snowscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class SnowScans : MangaThemesia(
    "Snow Scans",
    "https://snowscans.com",
    "en",
    mangaUrlDirectory = "/series",
)
