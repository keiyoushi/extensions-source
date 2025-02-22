package eu.kanade.tachiyomi.extension.en.manhuaus
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhuaUS : Madara("ManhuaUS", "https://manhuaus.com", "en") {

    override val useNewChapterEndpoint: Boolean = true

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
