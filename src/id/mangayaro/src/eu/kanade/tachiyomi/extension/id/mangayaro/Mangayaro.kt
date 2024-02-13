package eu.kanade.tachiyomi.extension.id.mangayaro

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class Mangayaro : MangaThemesia("Mangayaro", "https://www.mangayaro.id", "id") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(seniman) i"
}
