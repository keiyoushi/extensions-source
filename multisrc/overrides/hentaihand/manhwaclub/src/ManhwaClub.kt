package eu.kanade.tachiyomi.extension.en.manhwaclub

import eu.kanade.tachiyomi.multisrc.hentaihand.HentaiHand
import okhttp3.OkHttpClient

class ManhwaClub : HentaiHand("ManhwaClub", "https://manhwa.club", "en", true) {
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { authIntercept(it) }
        .build()
}
