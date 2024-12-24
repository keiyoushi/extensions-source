package eu.kanade.tachiyomi.extension.en.spiderscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class SpiderScans : Madara(
    "Spider Scans",
    "https://spidyscans.xyz",
    "en",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
