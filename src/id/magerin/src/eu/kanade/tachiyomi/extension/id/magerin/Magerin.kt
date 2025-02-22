package eu.kanade.tachiyomi.extension.id.magerin
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class Magerin : ZeistManga("Magerin", "https://www.magerin.com", "id") {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
