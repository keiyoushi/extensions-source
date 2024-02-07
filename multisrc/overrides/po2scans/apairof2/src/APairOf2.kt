package eu.kanade.tachiyomi.extension.en.apairof2

import eu.kanade.tachiyomi.multisrc.po2scans.PO2Scans
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class APairOf2 : PO2Scans("A Pair Of 2+", "https://po2scans.com", "en") {
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(4)
        .build()
}
