package eu.kanade.tachiyomi.extension.en.vyvymangaorg
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class VyvyMangaOrg : Madara(
    name = "VyvyManga.org",
    baseUrl = "https://vyvymanga.org",
    lang = "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
