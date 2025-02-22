package eu.kanade.tachiyomi.extension.pt.mahouscan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.slimereadtheme.SlimeReadTheme
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class MahouScan : SlimeReadTheme(
    "MahouScan",
    "https://mahouscan.com",
    "pt-BR",
    scanId = "1292193100",
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
