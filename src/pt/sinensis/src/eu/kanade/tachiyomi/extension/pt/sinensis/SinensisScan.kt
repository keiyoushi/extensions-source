package eu.kanade.tachiyomi.extension.pt.sinensis

import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class SinensisScan : PeachScan(
    "Sinensis Scan",
    "https://sinensis.leitorweb.com",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
