package eu.kanade.tachiyomi.extension.en.manhwa18org
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Manhwa18Org : Madara("Manhwa18.org", "https://manhwa18.org", "en") {

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
