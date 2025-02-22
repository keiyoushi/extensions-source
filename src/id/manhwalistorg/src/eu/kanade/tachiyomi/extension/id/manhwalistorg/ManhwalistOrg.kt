package eu.kanade.tachiyomi.extension.id.manhwalistorg
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwalistOrg : MangaThemesia(
    "Manhwalist.org",
    "https://manhwalist.org",
    "id",
    mangaUrlDirectory = "/manhwa",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
)
