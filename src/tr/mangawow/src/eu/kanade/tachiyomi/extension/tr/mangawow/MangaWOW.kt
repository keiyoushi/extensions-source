package eu.kanade.tachiyomi.extension.tr.mangawow
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaWOW : Madara(
    "MangaWOW",
    "https://mangawow.org",
    "tr",
    SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
)
