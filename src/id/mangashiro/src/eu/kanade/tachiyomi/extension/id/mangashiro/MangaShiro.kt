package eu.kanade.tachiyomi.extension.id.mangashiro

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MangaShiro : MangaThemesia("MangaShiro", "https://mangashiro.me", "id") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 5, TimeUnit.SECONDS)
        .build()

    override val hasProjectPage = true

    override val projectPageString = "/project-manga"

    override val seriesTitleSelector = ".ts-breadcrumb li:last-child span"
}
