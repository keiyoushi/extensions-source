package eu.kanade.tachiyomi.extension.en.mangaus
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaUS : Madara("MangaUS", "https://mangaus.xyz", "en") {
    override val pageListParseSelector = "img"
}
