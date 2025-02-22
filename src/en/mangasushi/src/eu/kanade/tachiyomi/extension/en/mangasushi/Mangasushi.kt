package eu.kanade.tachiyomi.extension.en.mangasushi
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Mangasushi : Madara("Mangasushi", "https://mangasushi.org", "en") {
    override val useNewChapterEndpoint: Boolean = true
}
