package eu.kanade.tachiyomi.extension.en.tecnoscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class TecnoScans : MangaThemesia(
    "Tecno Scans",
    "https://olyscans.xyz",
    "en",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
