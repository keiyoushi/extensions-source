package eu.kanade.tachiyomi.extension.en.readmanhwa

import eu.kanade.tachiyomi.multisrc.hentaihand.HentaiHand
import okhttp3.OkHttpClient

class ReadManhwa : HentaiHand("ReadManhwa", "https://readmanhwa.com", "en", true) {
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { authIntercept(it) }
        .build()
}
