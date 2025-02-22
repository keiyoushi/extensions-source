package eu.kanade.tachiyomi.extension.th.reapertrans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class ReaperTrans : MangaThemesia(
    "ReaperTrans",
    "https://reapertrans.com",
    "th",
)
