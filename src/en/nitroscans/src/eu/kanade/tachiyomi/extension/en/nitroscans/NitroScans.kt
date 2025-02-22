package eu.kanade.tachiyomi.extension.en.nitroscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class NitroScans : Madara("Nitro Scans", "https://nitroscans.net", "en") {
    override val id = 1310352166897986481

    override val mangaSubString = "mangas"

    override val filterNonMangaItems = false

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
