package eu.kanade.tachiyomi.extension.en.hentai20

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

class Hentai20 : MangaThemesia("Hentai20", "https://hentai20.io", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .build()
}
