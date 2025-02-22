package eu.kanade.tachiyomi.extension.en.readmangafree
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ReadMangaFree : Madara("ReadMangaFree", "https://readmangafree.net", "en") {
    override val useNewChapterEndpoint = false
}
