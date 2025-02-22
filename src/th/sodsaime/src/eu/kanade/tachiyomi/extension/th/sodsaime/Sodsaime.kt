package eu.kanade.tachiyomi.extension.th.sodsaime
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Sodsaime : MangaThemesia("สดใสเมะ", "https://www.xn--l3c0azab5a2gta.com", "th", dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("th")))
