package eu.kanade.tachiyomi.extension.en.spiderscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class SpiderScans : MangaThemesia(
    "Spider Scans",
    "https://spiderscans.xyz",
    "en",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
