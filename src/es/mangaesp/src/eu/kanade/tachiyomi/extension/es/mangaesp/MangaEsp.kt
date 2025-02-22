package eu.kanade.tachiyomi.extension.es.mangaesp

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaEsp : MangaThemesia(
    "MangaEsp",
    "https://mangaesp.topmanhuas.org",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("en")),
) {
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1, TimeUnit.SECONDS)
        .build()

    override val hasProjectPage = true

    override val projectPageString = "/proyectos"
}
