package eu.kanade.tachiyomi.extension.en.lyrascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class LyraScans : MangaThemesia("Lyra Scans", "https://lyrascans.com", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 5)
        .build()
}
