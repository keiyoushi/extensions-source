package eu.kanade.tachiyomi.extension.en.luminousscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class LuminousScans : MangaThemesia("Luminous Scans", "https://luminousscans.net", "en", mangaUrlDirectory = "/series") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
