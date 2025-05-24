package eu.kanade.tachiyomi.extension.pt.windscan

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class WindScan : GreenShit(
    "Wind Scan",
    "https://windscan.xyz",
    "pt-BR",
    scanId = 6,
) {
    // Moved from Madara to GreenShit
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
