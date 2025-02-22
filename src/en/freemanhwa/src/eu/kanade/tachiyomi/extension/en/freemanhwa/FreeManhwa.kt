package eu.kanade.tachiyomi.extension.en.freemanhwa
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class FreeManhwa : Madara("Free Manhwa", "https://manhwas.com", "en") {
    override val useNewChapterEndpoint = false
}
