package eu.kanade.tachiyomi.extension.en.manga18h
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Manga18h : Madara("Manga 18h", "https://manga18h.com", "en") {
    override val useNewChapterEndpoint = false
}
