package eu.kanade.tachiyomi.extension.tr.tarotscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class TarotScans : MangaThemesia("Tarot Scans", "https://www.tarotscans.com", "tr", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")))
