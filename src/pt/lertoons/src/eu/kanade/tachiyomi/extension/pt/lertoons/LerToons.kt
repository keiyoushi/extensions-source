package eu.kanade.tachiyomi.extension.pt.lertoons

import eu.kanade.tachiyomi.multisrc.zerotheme.ZeroTheme
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class LerToons : ZeroTheme(
    "Ler Toons",
    "https://lertoons.com",
    "pt-BR",
) {
    override val versionId = 3

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override val mangaSubString: String = "manga"
}
