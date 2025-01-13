package eu.kanade.tachiyomi.extension.pt.mahouscan

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
