package eu.kanade.tachiyomi.extension.pt.cerisescans

import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class CeriseScan : PeachScan("Cerise Scan", "https://cerisetoon.com", "pt-BR") {
    override val id: Long = 8629915907358523454

    override val versionId: Int = 2

    override val client = super.client.newBuilder()
        .rateLimit(3, 2)
        .build()
}
