package eu.kanade.tachiyomi.extension.pt.lscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class LScans : MangaThemesia(
    "L Scans",
    "https://lscans.com",
    "pt-BR",
) {
    // Moved from PeachScan to Mangathemsia
    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
