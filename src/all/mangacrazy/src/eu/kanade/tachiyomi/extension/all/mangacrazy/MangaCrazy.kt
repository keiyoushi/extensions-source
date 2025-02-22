package eu.kanade.tachiyomi.extension.all.mangacrazy
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaCrazy : Madara("MangaCrazy", "https://mangacrazy.net", "all") {
    override val useNewChapterEndpoint = true
}
