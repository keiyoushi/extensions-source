package eu.kanade.tachiyomi.extension.ko.manhwaraw
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhwaRaw : Madara("ManhwaRaw", "https://manhwaraw.com", "ko") {

    override val mangaSubString = "manhwa-raw"

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
