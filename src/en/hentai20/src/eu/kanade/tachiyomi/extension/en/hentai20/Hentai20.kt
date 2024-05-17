package eu.kanade.tachiyomi.extension.en.hentai20

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class Hentai20 : Madara("Hentai20", "https://hentai20.io", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .build()
}
