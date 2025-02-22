package eu.kanade.tachiyomi.extension.en.manhuanext
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Manhuanext : Madara(
    "Manhuanext",
    "https://manhuanext.com",
    "en",
) {
    override val useNewChapterEndpoint = true
}
