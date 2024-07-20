package eu.kanade.tachiyomi.extension.en.kaiscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class KaiScans : MangaThemesia("Kai Scans", "https://kaiscans.org", "en") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    // Kai Scans -> Umi Scans -> Kai Scans
    // override val id: Long = 4825368993215448425
}
