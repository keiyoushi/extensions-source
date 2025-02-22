package eu.kanade.tachiyomi.extension.fr.yaoiscan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class YaoiScan : MangaThemesia(
    name = "YaoiScan",
    baseUrl = "https://yaoiscan.fr",
    lang = "fr",
    mangaUrlDirectory = "/catalogue",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.FRANCE),
) {
    override val seriesStatusSelector = ".status-value"
}
