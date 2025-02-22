package eu.kanade.tachiyomi.extension.en.zinmanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Zinmanga : Madara("Zinmanga", "https://mangazin.org", "en") {

    // The website does not flag the content consistently.
    override val filterNonMangaItems = false
}
