package eu.kanade.tachiyomi.extension.ja.syosetuunoriginal

import eu.kanade.tachiyomi.multisrc.zing.Zing
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source

@Source
abstract class SyosetuUnoriginal : Zing() {
    override val mangaSubString = "manga"

    override val supportsLatest = false

    // The home page's latest updates grid, the site's only paginated list
    override suspend fun getPopularManga(page: Int): MangasPage = super.getLatestUpdates(page)
}
