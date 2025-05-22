package eu.kanade.tachiyomi.extension.en.mangakomi

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

class MangaKomi : Madara(
    "MangaKomi",
    "https://mangakomi.io",
    "en",
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .build()
}
