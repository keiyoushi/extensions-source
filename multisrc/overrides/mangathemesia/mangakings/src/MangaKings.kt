package eu.kanade.tachiyomi.extension.tr.mangakings

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl

class MangaKings : MangaThemesia("Manga Kings", "https://mangakings.com.tr", "tr") {
    override val client = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()
}
