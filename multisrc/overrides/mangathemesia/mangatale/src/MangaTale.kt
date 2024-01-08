package eu.kanade.tachiyomi.extension.id.mangatale

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class MangaTale : MangaThemesia("MangaTale", "https://mangatale.co", "id") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 5)
        .build()

    override val seriesTitleSelector = ".ts-breadcrumb li:last-child span"
}
