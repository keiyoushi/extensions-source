package eu.kanade.tachiyomi.extension.id.komikmanhwa

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class KomikManhwa : MangaThemesia("KomikManhwa", "https://komikmanhwa.me", "id", "/series") {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()
}
