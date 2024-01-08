package eu.kanade.tachiyomi.extension.id.komiktap

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class Komiktap : MangaThemesia("Komiktap", "https://komiktap.me", "id") {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()
}
