package eu.kanade.tachiyomi.extension.id.shirakami
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Shirakami : MangaThemesia(
    "Shirakami",
    "https://shirakami.xyz",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
)
