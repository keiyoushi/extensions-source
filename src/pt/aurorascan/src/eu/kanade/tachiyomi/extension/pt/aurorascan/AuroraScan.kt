package eu.kanade.tachiyomi.extension.pt.aurorascan

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class AuroraScan : GreenShit(
    "Aurora Scan",
    "https://aurorascan.net",
    "pt-BR",
    scanId = 4,
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
