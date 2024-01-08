package eu.kanade.tachiyomi.extension.ja.rawkuma

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class Rawkuma : MangaThemesia("Rawkuma", "https://rawkuma.com/", "ja") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()
}
