package eu.kanade.tachiyomi.extension.tr.araznovel
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ArazNovel : Madara(
    "ArazNovel",
    "https://araznovel.com",
    "tr",
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
)
