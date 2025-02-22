package eu.kanade.tachiyomi.extension.es.senpaiediciones
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class SenpaiEdiciones : MangaThemesia(
    "Senpai Ediciones",
    "https://senpaimangas.online",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val pageSelector = "div#readerarea img:not(noscript img)"
}
