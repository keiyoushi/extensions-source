package eu.kanade.tachiyomi.extension.th.ecchidoujin
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class EcchiDoujin : MangaThemesia(
    "Ecchi-Doujin",
    "https://ecchi-doujin.com",
    "th",
    "/doujin",
    SimpleDateFormat("MMMM d, yyyy", Locale("th")),
)
