package eu.kanade.tachiyomi.extension.en.kaiscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit

class KaiScans : MangaThemesia("Kai Scans", "https://kaiscans.org", "en") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
