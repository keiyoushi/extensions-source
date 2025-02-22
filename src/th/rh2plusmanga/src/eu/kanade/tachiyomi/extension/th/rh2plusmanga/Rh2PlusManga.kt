package eu.kanade.tachiyomi.extension.th.rh2plusmanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Rh2PlusManga : Madara("Rh2PlusManga", "https://www.rh2plusmanga.com", "th", SimpleDateFormat("d MMMM yyyy", Locale("th"))) {
    override val filterNonMangaItems = false

    override val pageListParseSelector = ".reading-content img"
}
