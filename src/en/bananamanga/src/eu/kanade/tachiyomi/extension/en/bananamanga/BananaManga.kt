package eu.kanade.tachiyomi.extension.en.bananamanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class BananaManga : Madara("Banana Manga", "https://bananamanga.net", "en") {
    override val useNewChapterEndpoint = true
}
