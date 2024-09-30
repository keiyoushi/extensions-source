package eu.kanade.tachiyomi.extension.id.kofiscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class KofiScans : MangaThemesia(
    "Kofi Scans",
    "https://kofiscans.me",
    "id",
    "/manhwa",
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
