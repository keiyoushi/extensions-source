package eu.kanade.tachiyomi.extension.en.kaiscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class KaiScans : MangaThemesia("Kai Scans", "https://kaiscans.org", "en") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
