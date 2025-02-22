package eu.kanade.tachiyomi.extension.en.manhwa68
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Manhwa68 : Madara(
    "Manhwa68",
    "https://manhwa68.com",
    "en",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US),
) {

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
