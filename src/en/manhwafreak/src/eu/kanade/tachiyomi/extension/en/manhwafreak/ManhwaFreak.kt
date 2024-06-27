package eu.kanade.tachiyomi.extension.en.manhwafreak

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl

class ManhwaFreak : MangaThemesia("Manhwa Freak", "https://manhwafreak.site", "en") {

    override val client = super.client.newBuilder().rateLimitHost(baseUrl.toHttpUrl(), 3, 1).build()
}
