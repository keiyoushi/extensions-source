package eu.kanade.tachiyomi.extension.pt.slimeread
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.slimereadtheme.SlimeReadTheme
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class SlimeRead : SlimeReadTheme(
    "SlimeRead",
    "https://slimeread.com",
    "pt-BR",
) {
    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
