package eu.kanade.tachiyomi.extension.en.kaiscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class KaiScans : MangaThemesiaAlt("Kai Scans", "https://kaiscans.com", "en") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
