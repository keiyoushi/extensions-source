package eu.kanade.tachiyomi.extension.en.shibamanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ShibaManga : Madara(
    "Shiba Manga",
    "https://shibamanga.com",
    "en",
    SimpleDateFormat("MM/dd/yyyy", Locale.US),
) {
    override val filterNonMangaItems = false
    override val useNewChapterEndpoint = true
}
