package eu.kanade.tachiyomi.extension.id.manhwalistid

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

class ManhwaList : MangaThemesia(
    "Manhwa List",
    "https://manhwalist01.com",
    "id",
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
