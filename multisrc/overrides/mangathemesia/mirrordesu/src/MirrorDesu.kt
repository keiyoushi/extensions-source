package eu.kanade.tachiyomi.extension.id.mirrordesu

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class MirrorDesu : MangaThemesia("MirrorDesu", "https://mirrordesu.me", "id", "/komik") {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val hasProjectPage = true
}
