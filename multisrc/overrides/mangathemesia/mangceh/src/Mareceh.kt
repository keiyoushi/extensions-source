package eu.kanade.tachiyomi.extension.id.mangceh

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class Mareceh : MangaThemesia("Mareceh", "https://mareceh.com", "id") {

    override val versionId = 2

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val hasProjectPage = true
}
