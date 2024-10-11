package eu.kanade.tachiyomi.extension.pt.lscans

import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class LScans : PeachScan(
    "L Scans",
    "https://lscans.com",
    "pt-BR",
) {
    // Moved from Madara to PeachScan
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
