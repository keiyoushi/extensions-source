package eu.kanade.tachiyomi.extension.pt.randomscan

import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class LuraToon : PeachScan("Lura Toon", "https://luratoon.com", "pt-BR") {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2)
        .build()
}
