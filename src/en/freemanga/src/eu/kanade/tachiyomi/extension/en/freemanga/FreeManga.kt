package eu.kanade.tachiyomi.extension.en.freemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

class FreeManga : Madara("Free Manga", "https://freemanga.me", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .build()
}
