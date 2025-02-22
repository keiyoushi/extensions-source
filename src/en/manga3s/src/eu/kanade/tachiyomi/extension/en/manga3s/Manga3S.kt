package eu.kanade.tachiyomi.extension.en.manga3s
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Manga3S : Madara("Manga3S", "https://manga3s.com", "en") {
    override val useNewChapterEndpoint: Boolean = true
}
