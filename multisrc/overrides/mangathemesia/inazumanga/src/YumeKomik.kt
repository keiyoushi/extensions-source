package eu.kanade.tachiyomi.extension.id.inazumanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class YumeKomik : MangaThemesia("YumeKomik", "https://yumekomik.com", "id") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val hasProjectPage = true
}
