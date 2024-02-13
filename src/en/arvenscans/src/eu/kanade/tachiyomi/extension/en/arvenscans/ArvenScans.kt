package eu.kanade.tachiyomi.extension.en.arvenscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ArvenScans : MangaThemesia("Arven Scans", "https://arvenscans.com", "en", "/series") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 5, TimeUnit.SECONDS)
        .build()
}
