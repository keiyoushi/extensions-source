package eu.kanade.tachiyomi.extension.pt.hikariganai

import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class HikariGaNai : PeachScan(
    "Hikari Ga Nai",
    "https://hikariganai.xyz",
    "pt-BR",
) {
    // Moved from Madara to PeachScan
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
