package eu.kanade.tachiyomi.extension.en.mangafreakonline
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaFreakOnline : Madara(
    "MangaFreak.online",
    "https://mangafreak.online",
    "en",
    dateFormat = SimpleDateFormat("d MMM، yyy", Locale.US),
) {
    override val useNewChapterEndpoint = false
}
