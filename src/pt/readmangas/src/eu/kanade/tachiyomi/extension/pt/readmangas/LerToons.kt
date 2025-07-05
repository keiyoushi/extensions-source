package eu.kanade.tachiyomi.extension.pt.readmangas

import eu.kanade.tachiyomi.multisrc.zerotheme.ZeroTheme
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class LerToons : ZeroTheme(
    "Ler Toons",
    "https://lertoons.com",
    "pt-BR",
) {
    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override val versionId = 3
}
