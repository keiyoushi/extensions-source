package eu.kanade.tachiyomi.extension.pt.cerisescans

import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class CeriseScan : PeachScan(
    "Cerise Scan",
    "https://sctoon.net",
    "pt-BR",
) {
    override val versionId: Int = 2

    override val client = super.client.newBuilder()
        .rateLimit(1, 2)
        .build()
}
