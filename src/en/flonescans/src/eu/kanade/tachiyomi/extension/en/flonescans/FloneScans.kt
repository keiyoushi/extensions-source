package eu.kanade.tachiyomi.extension.en.flonescans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class FloneScans : MangaThemesia(
    "Flone Scans",
    "https://sweetmanhwa.online",
    "en",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH),
)
