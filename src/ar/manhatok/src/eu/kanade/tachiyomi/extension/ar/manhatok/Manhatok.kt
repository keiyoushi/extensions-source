package eu.kanade.tachiyomi.extension.ar.manhatok
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class Manhatok : ZeistManga("Manhatok", "https://manhatok.blogspot.com", "ar") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
